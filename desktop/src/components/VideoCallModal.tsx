import React, { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Mic, MicOff, Video, VideoOff, PhoneOff } from 'lucide-react'
import { useXmtp } from '../context/XmtpContext'

interface VideoCallModalProps {
  conversationId: string
  isIncoming: boolean
  onClose: () => void
}

export function VideoCallModal({ conversationId, isIncoming, onClose }: VideoCallModalProps) {
  const { sendMessage, incomingSignal, clearIncomingSignal } = useXmtp()
  const [isAudioMuted, setIsAudioMuted] = useState(false)
  const [isVideoMuted, setIsVideoMuted] = useState(false)
  
  const localVideoRef = useRef<HTMLVideoElement>(null)
  const remoteVideoRef = useRef<HTMLVideoElement>(null)
  const pcRef = useRef<RTCPeerConnection | null>(null)
  const localStreamRef = useRef<MediaStream | null>(null)

  useEffect(() => {
    async function startCall() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
        localStreamRef.current = stream
        if (localVideoRef.current) {
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

        if (!isIncoming) {
          const offer = await pc.createOffer()
          await pc.setLocalDescription(offer)
          sendMessage(JSON.stringify({
            type: 'webrtc_offer',
            sdp: offer.sdp
          }))
        } else if (incomingSignal && incomingSignal.type === 'webrtc_offer') {
          await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: incomingSignal.sdp }))
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          sendMessage(JSON.stringify({
            type: 'webrtc_answer',
            sdp: answer.sdp
          }))
          clearIncomingSignal()
        }
      } catch (e) {
        console.error("Failed to start WebRTC", e)
      }
    }
    
    startCall()

    return () => {
      localStreamRef.current?.getTracks().forEach(t => t.stop())
      pcRef.current?.close()
    }
  }, []) // Empty dep array because we only want to init once

  // Listen for incoming signals during the call (answers, ICE candidates, hang ups)
  useEffect(() => {
    if (!incomingSignal || incomingSignal.conversationId !== conversationId) return
    const pc = pcRef.current
    if (!pc) return

    async function handleSignal() {
      try {
        if (incomingSignal.type === 'webrtc_answer') {
          await pc?.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: incomingSignal.sdp }))
        } else if (incomingSignal.type === 'webrtc_ice_candidate') {
          await pc?.addIceCandidate(new RTCIceCandidate({
            candidate: incomingSignal.candidate,
            sdpMid: incomingSignal.sdpMid,
            sdpMLineIndex: incomingSignal.sdpMLineIndex
          }))
        } else if (incomingSignal.type === 'webrtc_call_end') {
          onClose()
        }
        clearIncomingSignal()
      } catch (e) {
        console.error("Failed handling WebRTC signal", e)
      }
    }
    handleSignal()
  }, [incomingSignal])

  const toggleMute = () => {
    const audioTracks = localStreamRef.current?.getAudioTracks()
    if (audioTracks && audioTracks.length > 0) {
      audioTracks[0].enabled = isAudioMuted
      setIsAudioMuted(!isAudioMuted)
    }
  }

  const toggleVideo = () => {
    const videoTracks = localStreamRef.current?.getVideoTracks()
    if (videoTracks && videoTracks.length > 0) {
      videoTracks[0].enabled = isVideoMuted
      setIsVideoMuted(!isVideoMuted)
    }
  }

  const handleEndCall = () => {
    sendMessage(JSON.stringify({ type: 'webrtc_call_end' }))
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm">
      <motion.div 
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        className="relative w-full max-w-4xl bg-gray-900 rounded-2xl overflow-hidden shadow-2xl"
        style={{ aspectRatio: '16/9' }}
      >
        {/* Remote Video */}
        <video 
          ref={remoteVideoRef} 
          autoPlay 
          playsInline
          className="w-full h-full object-cover"
        />

        {/* Local Video PIP */}
        <div className="absolute top-4 right-4 w-48 h-36 bg-gray-800 rounded-xl overflow-hidden shadow-lg border-2 border-gray-700">
          <video 
            ref={localVideoRef}
            autoPlay
            playsInline
            muted
            className="w-full h-full object-cover transform scale-x-[-1]"
          />
        </div>

        {/* Controls Overlay */}
        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex items-center gap-6 px-8 py-4 bg-gray-900/60 backdrop-blur-md rounded-full shadow-lg border border-gray-700/50">
          <button 
            onClick={toggleMute}
            className={`p-4 rounded-full transition-all ${isAudioMuted ? 'bg-red-500/20 text-red-500 hover:bg-red-500/30' : 'bg-gray-700/50 text-white hover:bg-gray-700'}`}
          >
            {isAudioMuted ? <MicOff size={24} /> : <Mic size={24} />}
          </button>
          
          <button 
            onClick={handleEndCall}
            className="p-5 bg-red-600 text-white rounded-full hover:bg-red-700 shadow-[0_0_20px_rgba(220,38,38,0.4)] transition-all hover:scale-105"
          >
            <PhoneOff size={28} />
          </button>

          <button 
            onClick={toggleVideo}
            className={`p-4 rounded-full transition-all ${isVideoMuted ? 'bg-red-500/20 text-red-500 hover:bg-red-500/30' : 'bg-gray-700/50 text-white hover:bg-gray-700'}`}
          >
            {isVideoMuted ? <VideoOff size={24} /> : <Video size={24} />}
          </button>
        </div>
      </motion.div>
    </div>
  )
}
