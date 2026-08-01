import React, { useEffect, useRef, useState, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Mic, MicOff, Video, VideoOff, PhoneOff, Phone, PhoneCall } from 'lucide-react'
import { useXmtp } from '../context/XmtpContext'

interface CallModalProps {
  conversationId: string
  isIncoming: boolean
  isVoiceOnly: boolean
  callerName: string
  onClose: (duration: number) => void
}

type CallState = 'waiting' | 'connected' | 'ended'

function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

export function VideoCallModal({ conversationId, isIncoming, isVoiceOnly, callerName, onClose }: CallModalProps) {
  const { sendMessage, incomingSignal, clearIncomingSignal } = useXmtp()
  const [isAudioMuted, setIsAudioMuted] = useState(false)
  const [isVideoMuted, setIsVideoMuted] = useState(isVoiceOnly)
  const [callState, setCallState] = useState<CallState>('waiting')
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  const localVideoRef = useRef<HTMLVideoElement>(null)
  const remoteVideoRef = useRef<HTMLVideoElement>(null)
  const pcRef = useRef<RTCPeerConnection | null>(null)
  const localStreamRef = useRef<MediaStream | null>(null)
  const timerRef = useRef<NodeJS.Timeout | null>(null)
  const startTimeRef = useRef<number>(0)

  const startTimer = useCallback(() => {
    startTimeRef.current = Date.now()
    timerRef.current = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startTimeRef.current) / 1000))
    }, 1000)
  }, [])

  useEffect(() => {
    async function startCall() {
      try {
        const constraints = isVoiceOnly
          ? { video: false, audio: true }
          : { video: true, audio: true }

        const stream = await navigator.mediaDevices.getUserMedia(constraints)
        localStreamRef.current = stream
        if (localVideoRef.current && !isVoiceOnly) {
          localVideoRef.current.srcObject = stream
        }

        const pc = new RTCPeerConnection({
          iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
        })
        pcRef.current = pc

        stream.getTracks().forEach(track => pc.addTrack(track, stream))

        pc.ontrack = (event) => {
          if (remoteVideoRef.current && event.streams[0]) {
            remoteVideoRef.current.srcObject = event.streams[0]
          }
          // When we get remote tracks, call is connected
          setCallState('connected')
          startTimer()
        }

        pc.onicecandidate = (event) => {
          if (event.candidate) {
            sendMessage(JSON.stringify({
              type: 'webrtc_ice_candidate',
              candidate: event.candidate.candidate,
              sdpMid: event.candidate.sdpMid,
              sdpMLineIndex: event.candidate.sdpMLineIndex
            }))
          }
        }

        pc.onconnectionstatechange = () => {
          if (pc.connectionState === 'connected') {
            setCallState('connected')
            startTimer()
          }
        }

        if (!isIncoming) {
          const offer = await pc.createOffer()
          await pc.setLocalDescription(offer)
          sendMessage(JSON.stringify({
            type: 'webrtc_offer',
            sdp: offer.sdp,
            isVoiceOnly,
          }))
        } else if (incomingSignal?.type === 'webrtc_offer') {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: incomingSignal.sdp }))
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          sendMessage(JSON.stringify({
            type: 'webrtc_answer',
            sdp: answer.sdp
          }))
          clearIncomingSignal()
          setCallState('connected')
          startTimer()
        }
      } catch (e) {
        console.error('Failed to start WebRTC', e)
      }
    }

    startCall()

    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
      localStreamRef.current?.getTracks().forEach(t => t.stop())
      pcRef.current?.close()
    }
  }, [])

  // Handle incoming signals
  useEffect(() => {
    if (!incomingSignal || incomingSignal.conversationId !== conversationId) return
    const pc = pcRef.current
    if (!pc) return

    async function handleSignal() {
      try {
        if (incomingSignal.type === 'webrtc_answer') {
          await pc?.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: incomingSignal.sdp }))
          setCallState('connected')
          startTimer()
        } else if (incomingSignal.type === 'webrtc_ice_candidate') {
          await pc?.addIceCandidate(new RTCIceCandidate({
            candidate: incomingSignal.candidate,
            sdpMid: incomingSignal.sdpMid,
            sdpMLineIndex: incomingSignal.sdpMLineIndex
          }))
        } else if (incomingSignal.type === 'webrtc_call_end') {
          if (timerRef.current) clearInterval(timerRef.current)
          onClose(elapsedSeconds)
        }
        clearIncomingSignal()
      } catch (e) {
        console.error('Failed handling WebRTC signal', e)
      }
    }
    handleSignal()
  }, [incomingSignal])

  const toggleMute = () => {
    const audioTracks = localStreamRef.current?.getAudioTracks()
    if (audioTracks?.[0]) {
      audioTracks[0].enabled = isAudioMuted
      setIsAudioMuted(!isAudioMuted)
    }
  }

  const toggleVideo = () => {
    const videoTracks = localStreamRef.current?.getVideoTracks()
    if (videoTracks?.[0]) {
      videoTracks[0].enabled = isVideoMuted
      setIsVideoMuted(!isVideoMuted)
    }
  }

  const handleEndCall = () => {
    sendMessage(JSON.stringify({ type: 'webrtc_call_end' }))
    if (timerRef.current) clearInterval(timerRef.current)
    onClose(elapsedSeconds)
  }

  const isWaiting = callState === 'waiting'

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(12px)',
    }}>
      <motion.div
        initial={{ opacity: 0, scale: 0.92 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.92 }}
        style={{
          position: 'relative',
          width: isVoiceOnly ? 340 : '90vw',
          maxWidth: isVoiceOnly ? 340 : 900,
          aspectRatio: isVoiceOnly ? 'auto' : '16/9',
          background: isVoiceOnly ? 'linear-gradient(145deg, #1a1a2e, #16213e, #0f3460)' : '#111',
          borderRadius: 24,
          overflow: 'hidden',
          boxShadow: '0 32px 80px rgba(0,0,0,0.6)',
          border: '1px solid rgba(255,255,255,0.08)',
          padding: isVoiceOnly ? '40px 32px 32px' : undefined,
          display: 'flex',
          flexDirection: 'column',
          alignItems: isVoiceOnly ? 'center' : undefined,
        }}
      >
        {/* ── Voice-only Layout ── */}
        {isVoiceOnly ? (
          <>
            {/* Avatar circle */}
            <div style={{
              width: 96, height: 96, borderRadius: '50%',
              background: 'linear-gradient(135deg, #667eea, #764ba2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 36, fontWeight: 700, color: 'white',
              marginBottom: 20,
              boxShadow: isWaiting
                ? '0 0 0 0 rgba(102,126,234,0.6)'
                : '0 0 0 16px rgba(102,126,234,0.15)',
              animation: isWaiting ? 'pulse-ring 1.5s infinite' : 'none',
            }}>
              {callerName[0]?.toUpperCase() ?? '?'}
            </div>

            <div style={{ color: 'white', fontSize: 22, fontWeight: 700, marginBottom: 6 }}>
              {callerName}
            </div>

            <div style={{ color: 'rgba(255,255,255,0.6)', fontSize: 14, marginBottom: 32 }}>
              {isWaiting
                ? (isIncoming ? 'Incoming voice call…' : 'Waiting for participant(s)...')
                : formatDuration(elapsedSeconds)
              }
            </div>

            {/* Controls */}
            <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
              <button
                onClick={toggleMute}
                style={{
                  width: 56, height: 56, borderRadius: '50%', border: 'none', cursor: 'pointer',
                  background: isAudioMuted ? 'rgba(239,68,68,0.2)' : 'rgba(255,255,255,0.1)',
                  color: isAudioMuted ? '#ef4444' : 'white',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  transition: 'all 0.2s',
                }}
              >
                {isAudioMuted ? <MicOff size={22} /> : <Mic size={22} />}
              </button>

              <button
                onClick={handleEndCall}
                style={{
                  width: 72, height: 72, borderRadius: '50%', border: 'none', cursor: 'pointer',
                  background: '#ef4444',
                  color: 'white',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  boxShadow: '0 0 24px rgba(239,68,68,0.4)',
                  transition: 'all 0.2s',
                }}
              >
                <PhoneOff size={28} />
              </button>
            </div>
          </>
        ) : (
          /* ── Video Layout ── */
          <>
            {/* Waiting overlay */}
            <AnimatePresence>
              {isWaiting && (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  style={{
                    position: 'absolute', inset: 0, zIndex: 10,
                    display: 'flex', flexDirection: 'column',
                    alignItems: 'center', justifyContent: 'center',
                    background: 'linear-gradient(145deg, #1a1a2e, #16213e)',
                  }}
                >
                  <div style={{
                    width: 100, height: 100, borderRadius: '50%',
                    background: 'linear-gradient(135deg, #667eea, #764ba2)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 40, fontWeight: 700, color: 'white',
                    marginBottom: 20,
                    boxShadow: '0 0 0 20px rgba(102,126,234,0.12)',
                  }}>
                    {callerName[0]?.toUpperCase() ?? '?'}
                  </div>
                  <div style={{ color: 'white', fontSize: 24, fontWeight: 700, marginBottom: 8 }}>
                    {callerName}
                  </div>
                  <div style={{ color: 'rgba(255,255,255,0.5)', fontSize: 15, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ animation: 'dots 1.5s infinite' }}>
                      {isIncoming ? 'Incoming video call…' : 'Ringing…'}
                    </span>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Remote video */}
            <video ref={remoteVideoRef} autoPlay playsInline
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />

            {/* Local video PIP */}
            {!isVoiceOnly && !isVideoMuted && (
              <div style={{
                position: 'absolute', top: 16, right: 16,
                width: 180, height: 120,
                background: '#222', borderRadius: 12,
                overflow: 'hidden',
                border: '2px solid rgba(255,255,255,0.15)',
                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
              }}>
                <video ref={localVideoRef} autoPlay playsInline muted
                  style={{ width: '100%', height: '100%', objectFit: 'cover', transform: 'scaleX(-1)' }}
                />
              </div>
            )}

            {/* Timer when connected */}
            {callState === 'connected' && (
              <div style={{
                position: 'absolute', top: 16, left: '50%', transform: 'translateX(-50%)',
                background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(8px)',
                color: 'white', padding: '6px 16px', borderRadius: 20,
                fontSize: 14, fontWeight: 600, letterSpacing: 1,
              }}>
                {formatDuration(elapsedSeconds)}
              </div>
            )}

            {/* Controls */}
            <div style={{
              position: 'absolute', bottom: 28, left: '50%', transform: 'translateX(-50%)',
              display: 'flex', gap: 20, alignItems: 'center',
              background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(12px)',
              padding: '14px 28px', borderRadius: 50,
              border: '1px solid rgba(255,255,255,0.1)',
            }}>
              <button onClick={toggleMute} style={{
                width: 52, height: 52, borderRadius: '50%', border: 'none', cursor: 'pointer',
                background: isAudioMuted ? 'rgba(239,68,68,0.25)' : 'rgba(255,255,255,0.12)',
                color: isAudioMuted ? '#ef4444' : 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.2s',
              }}>
                {isAudioMuted ? <MicOff size={22} /> : <Mic size={22} />}
              </button>

              <button onClick={handleEndCall} style={{
                width: 64, height: 64, borderRadius: '50%', border: 'none', cursor: 'pointer',
                background: '#ef4444', color: 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: '0 0 24px rgba(239,68,68,0.45)', transition: 'all 0.2s',
              }}>
                <PhoneOff size={26} />
              </button>

              <button onClick={toggleVideo} style={{
                width: 52, height: 52, borderRadius: '50%', border: 'none', cursor: 'pointer',
                background: isVideoMuted ? 'rgba(239,68,68,0.25)' : 'rgba(255,255,255,0.12)',
                color: isVideoMuted ? '#ef4444' : 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'all 0.2s',
              }}>
                {isVideoMuted ? <VideoOff size={22} /> : <Video size={22} />}
              </button>
            </div>
          </>
        )}
      </motion.div>

      <style>{`
        @keyframes pulse-ring {
          0% { box-shadow: 0 0 0 0 rgba(102,126,234,0.6); }
          70% { box-shadow: 0 0 0 20px rgba(102,126,234,0); }
          100% { box-shadow: 0 0 0 0 rgba(102,126,234,0); }
        }
      `}</style>
    </div>
  )
}
