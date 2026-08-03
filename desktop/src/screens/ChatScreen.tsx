import { useEffect, useRef, useState, useCallback, useMemo } from 'react'
import { useXmtp } from '../context/XmtpContext'
import { DecodedMessage } from '@xmtp/browser-sdk'
import ProfileDetailsModal from '../components/ProfileDetailsModal'
import { startRecording, RecordingHandle } from '../services/AudioRecorder'
import { Virtuoso, VirtuosoHandle } from 'react-virtuoso'
import React from 'react'
import { getClearedUpTo } from '../services/metadataStore'
import { VideoCallModal } from '../components/VideoCallModal'
import { Phone, Video } from 'lucide-react'
import { RemoteAttachmentCodec } from '@xmtp/content-type-remote-attachment'

function formatTime(date: Date) {
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatDay(date: Date) {
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return 'Today'
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

// ── Audio Player Bubble ──────────────────────────────────────────────────────

function AudioPlayer({ src, filename }: { src: string; filename?: string }) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(0)
  const [duration, setDuration] = useState(0)

  const toggle = () => {
    const a = audioRef.current
    if (!a) return
    if (playing) { a.pause() } else { a.play() }
    setPlaying(!playing)
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 200, padding: '4px 0' }}>
      <audio
        ref={audioRef}
        src={src}
        onTimeUpdate={e => {
          const a = e.currentTarget
          setProgress(a.duration ? a.currentTime / a.duration : 0)
        }}
        onLoadedMetadata={e => setDuration(e.currentTarget.duration)}
        onEnded={() => setPlaying(false)}
        style={{ display: 'none' }}
      />
      <button
        onClick={toggle}
        style={{
          width: 36, height: 36, borderRadius: '50%',
          background: 'rgba(255,255,255,0.2)',
          border: 'none', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 16, color: 'inherit', flexShrink: 0,
        }}
      >
        {playing ? '⏸' : '▶'}
      </button>
      <div style={{ flex: 1 }}>
        <div style={{
          height: 4, background: 'rgba(255,255,255,0.2)', borderRadius: 2,
          position: 'relative', cursor: 'pointer',
        }}
          onClick={e => {
            const rect = e.currentTarget.getBoundingClientRect()
            const ratio = (e.clientX - rect.left) / rect.width
            if (audioRef.current) { audioRef.current.currentTime = ratio * audioRef.current.duration }
          }}
        >
          <div style={{ width: `${progress * 100}%`, height: '100%', background: 'currentColor', borderRadius: 2, opacity: 0.7 }} />
        </div>
        <div style={{ fontSize: 10, opacity: 0.7, marginTop: 2 }}>
          {duration > 0 ? formatDuration(duration) : filename ?? 'Voice memo'}
        </div>
      </div>
    </div>
  )
}

// ── Reaction Toolbar ─────────────────────────────────────────────────────────

const REACTION_EMOJIS = ['❤️', '👍', '😂', '😮', '😢', '🔥']

function ReactionToolbar({ onReact, onCopy, text }: { onReact: (e: string) => void; onCopy: () => void; text?: string }) {
  return (
    <div style={{
      position: 'absolute', bottom: '100%', left: '50%', transform: 'translateX(-50%)',
      background: 'var(--surface)', border: '1px solid var(--border)',
      borderRadius: 24, padding: '6px 10px',
      display: 'flex', alignItems: 'center', gap: 4,
      boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
      zIndex: 10, whiteSpace: 'nowrap',
    }}>
      {REACTION_EMOJIS.map(e => (
        <button
          key={e}
          onClick={() => onReact(e)}
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: 20, padding: '2px 4px', borderRadius: 8,
            transition: 'transform 0.1s',
          }}
          onMouseEnter={ev => (ev.currentTarget.style.transform = 'scale(1.3)')}
          onMouseLeave={ev => (ev.currentTarget.style.transform = 'scale(1)')}
        >
          {e}
        </button>
      ))}
      {text && (
        <button
          onClick={onCopy}
          title="Copy"
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: 14, padding: '2px 6px', borderRadius: 8,
            color: 'var(--text-secondary)',
          }}
        >
          📋
        </button>
      )}
    </div>
  )
}

// ── Remote Attachment Renderer ───────────────────────────────────────────────

function hexToUint8Array(hexString: string) {
  if (!hexString || typeof hexString !== 'string') return hexString
  const bytes = new Uint8Array(hexString.length / 2)
  for (let i = 0; i < hexString.length; i += 2) {
    bytes[i / 2] = parseInt(hexString.substr(i, 2), 16)
  }
  return bytes
}

function RemoteAttachmentRenderer({ content, client }: { content: any; client: any }) {
  const [decrypted, setDecrypted] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (decrypted || loading) return
    let mounted = true
    const load = async () => {
      setLoading(true)
      try {
        const payload = { ...content }
        if (typeof payload.contentDigest === 'string') payload.contentDigest = hexToUint8Array(payload.contentDigest)
        if (typeof payload.salt === 'string') payload.salt = hexToUint8Array(payload.salt)
        if (typeof payload.nonce === 'string') payload.nonce = hexToUint8Array(payload.nonce)
        if (typeof payload.secret === 'string') payload.secret = hexToUint8Array(payload.secret)

        const decoded = await RemoteAttachmentCodec.load(payload, client)
        if (mounted) setDecrypted(decoded)
      } catch (err) {
        console.error('Failed to load remote attachment:', err)
        if (mounted) setError('Failed to decrypt attachment')
      } finally {
        if (mounted) setLoading(false)
      }
    }
    load()
    return () => { mounted = false }
  }, [content, client, decrypted, loading])

  if (error) {
    return <div className="chat-text" style={{ color: 'var(--error)' }}>{error}</div>
  }
  if (!decrypted) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'var(--surface-variant)', borderRadius: 12 }}>
        <div style={{ width: 24, height: 24, border: '2px solid var(--primary)', borderTopColor: 'transparent', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
        <div>
          <div style={{ fontSize: 13, fontWeight: 600 }}>Downloading {content.filename || 'Attachment'}...</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>{(content.contentLength / 1024).toFixed(1)} KB</div>
        </div>
      </div>
    )
  }

  return <BubbleContent msg={{ content: decrypted } as any} />
}

// ── Bubble Content Renderer ──────────────────────────────────────────────────

function BubbleContent({ msg, onSystemAction, client }: { msg: DecodedMessage, onSystemAction?: (action: string) => void, client?: any }) {
  const content = msg.content as any

  if (typeof content === 'string' && content.startsWith('SYSTEM_UI:')) {
    const parts = content.split(':')
    const type = parts[1]
    const tsStr = parts[2]
    switch (type) {
      case 'clear_history_request_sent':
        return <div className="chat-text" style={{ fontStyle: 'italic', opacity: 0.8 }}>You requested to clear history for both users. Waiting for consent...</div>
      case 'clear_history_request':
        return (
          <div className="chat-text" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ fontWeight: 'bold' }}>Contact requested to clear the chat history for both users.</div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="primary-btn" style={{ background: 'var(--error)' }} onClick={() => onSystemAction?.('accept_clear:' + tsStr)}>Accept</button>
              <button className="secondary-btn" onClick={() => onSystemAction?.('decline_clear')}>Decline</button>
            </div>
          </div>
        )
      case 'clear_history_accept':
        return <div className="chat-text" style={{ fontStyle: 'italic', opacity: 0.8 }}>History cleared by consent.</div>
      case 'clear_history_decline':
        return <div className="chat-text" style={{ fontStyle: 'italic', opacity: 0.8 }}>Contact declined the clear history request.</div>
      case 'call_started': {
        const timeString = new Date((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        const rest = tsStr?.replace(/^[📞📹]\s*/, '') ?? 'Call started'
        return (
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8, fontStyle: 'italic',
            opacity: 0.85, padding: '4px 0', fontSize: 13,
          }}>
            <span style={{ fontSize: 16 }}>{tsStr?.startsWith('📞') ? '📞' : '📹'}</span>
            <span>{rest} at {timeString}</span>
          </div>
        )
      }
      case 'call_ended': {
        const timeString = new Date((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        const baseText = parts.slice(2).join(':') || 'Call ended'
        const cleanText = baseText.replace(/^[📞📹]\s*/, '').trim()
        const durationPart = cleanText.includes('•') ? ' • ' + cleanText.split('•')[1].trim() : ''
        const callPart = cleanText.includes('•') ? cleanText.split('•')[0].trim() : cleanText
        return (
          <div style={{
            display: 'flex', alignItems: 'center', gap: 8, fontStyle: 'italic',
            opacity: 0.75, padding: '4px 0', fontSize: 13,
          }}>
            <span style={{ fontSize: 16 }}>📵</span>
            <span>{callPart} at {timeString}{durationPart}</span>
          </div>
        )
      }
    }
  }

  // Parse structural JSON payloads coming directly over XMTP
  if (typeof content === 'string' && content.trim().startsWith('{')) {
    try {
      const json = JSON.parse(content)
      if (json.type === 'clear_history_request') {
        return (
          <div className="chat-text" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ fontWeight: 'bold' }}>Contact requested to clear the chat history for both users.</div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="primary-btn" style={{ background: 'var(--error)' }} onClick={() => onSystemAction?.('accept_clear:' + json.timestamp)}>Accept</button>
              <button className="secondary-btn" onClick={() => onSystemAction?.('decline_clear')}>Decline</button>
            </div>
          </div>
        )
      } else if (json.type === 'call_event') {
        return null
      } else if (json.type === 'attachment') {
        return <RemoteAttachmentRenderer content={json} client={client} />
      }
    } catch {}
  }

  // Remote attachment metadata
  if (content && typeof content === 'object' && content.url && content.secret) {
    return <RemoteAttachmentRenderer content={content} client={client} />
  }

  // Inline attachment (small file via AttachmentCodec)
  if (content && content.mimeType && content.data instanceof Uint8Array) {
    const blob = new Blob([content.data], { type: content.mimeType })
    const url = URL.createObjectURL(blob)

    if (content.mimeType.startsWith('image/')) {
      return (
        <img
          src={url}
          alt={content.filename}
          style={{ maxWidth: '100%', borderRadius: 8, marginTop: 4, marginBottom: 4, display: 'block' }}
        />
      )
    }
    if (content.mimeType.startsWith('video/')) {
      return (
        <video
          controls
          style={{ maxWidth: '100%', maxHeight: 280, borderRadius: 8, marginTop: 4, marginBottom: 4, display: 'block' }}
        >
          <source src={url} type={content.mimeType} />
        </video>
      )
    }
    if (content.mimeType.startsWith('audio/')) {
      return <AudioPlayer src={url} filename={content.filename} />
    }
    // Generic file chip
    return (
      <a
        href={url}
        download={content.filename}
        style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 4px', color: 'inherit', textDecoration: 'none' }}
      >
        <span style={{ fontSize: 22 }}>
          {content.mimeType === 'application/pdf' ? '📄' : '📎'}
        </span>
        <div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>{content.filename}</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>{content.mimeType}</div>
        </div>
      </a>
    )
  }

  // Decoded remote attachment (downloaded + decrypted by XMTP SDK)
  if (content && typeof content === 'object' && content.mimeType && content.data) {
    const bytes = content.data instanceof Uint8Array ? content.data : new Uint8Array(content.data)
    const blob = new Blob([bytes], { type: content.mimeType })
    const url = URL.createObjectURL(blob)

    if (content.mimeType.startsWith('image/')) {
      return <img src={url} alt={content.filename} style={{ maxWidth: '100%', borderRadius: 8, display: 'block' }} />
    }
    if (content.mimeType.startsWith('video/')) {
      return (
        <video controls style={{ maxWidth: '100%', maxHeight: 280, borderRadius: 8, display: 'block' }}>
          <source src={url} type={content.mimeType} />
        </video>
      )
    }
    if (content.mimeType.startsWith('audio/')) {
      return <AudioPlayer src={url} filename={content.filename} />
    }
    return (
      <a href={url} download={content.filename}
        style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 4px', color: 'inherit', textDecoration: 'none' }}
      >
        <span style={{ fontSize: 22 }}>{content.mimeType === 'application/pdf' ? '📄' : '📎'}</span>
        <div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>{content.filename}</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>{content.mimeType}</div>
        </div>
      </a>
    )
  }

  // Reply payload
  if (content && typeof content === 'object' && content.type === 'reply') {
    return <div className="chat-text">{content.content}</div>
  }

  // Plain text
  const text = typeof content === 'string' ? content : JSON.stringify(content)
  return <div className="chat-text">{text}</div>
}

// ── Main Chat Screen ─────────────────────────────────────────────────────────

export default function ChatScreen() {
  const { activeConversationId, selectConversation, messages, sendMessage, sendReaction, sendAttachment, messagesLoading, client, typingUsers, reactions, sendVoiceMemo, incomingSignal, clearIncomingSignal, conversations, refreshConversations } = useXmtp()
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [showProfile, setShowProfile] = useState(false)
  const [showClearDialog, setShowClearDialog] = useState(false)
  const [showCallPicker, setShowCallPicker] = useState(false)
  const [activeCall, setActiveCall] = useState<{ isVoiceOnly: boolean; isIncoming: boolean } | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null)
  const lastTypingSentAtRef = useRef<number>(0)
  // Voice memo state
  const [recording, setRecording] = useState(false)
  const [recordingSeconds, setRecordingSeconds] = useState(0)
  const recordingHandleRef = useRef<RecordingHandle | null>(null)
  const recordingTimerRef = useRef<NodeJS.Timeout | null>(null)

  const activeMeta = conversations.find(c => c.id === activeConversationId)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    const trimmed = text.trim()
    if (!trimmed || sending) return
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    lastTypingSentAtRef.current = 0
    sendMessage(JSON.stringify({ type: 'typing', isTyping: false })).catch(console.error)
    setSending(true)
    setText('')
    try { await sendMessage(trimmed) } catch (e) { console.error('Send failed', e) }
    finally { setSending(false); textareaRef.current?.focus() }
  }

  const handleAttach = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || sending) return
    setSending(true)
    try { await sendAttachment(file) }
    catch (err) { console.error('Failed to send attachment', err); alert('Failed to send attachment') }
    finally { setSending(false); if (fileInputRef.current) fileInputRef.current.value = '' }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend() }
  }

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const val = e.target.value
    setText(val)
    const t = e.target
    t.style.height = 'auto'
    t.style.height = `${Math.min(t.scrollHeight, 140)}px`
    const now = Date.now()
    if (val.trim()) {
      if (now - lastTypingSentAtRef.current > 2000) {
        lastTypingSentAtRef.current = now
        sendMessage(JSON.stringify({ type: 'typing', isTyping: true })).catch(console.error)
      }
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
      typingTimeoutRef.current = setTimeout(() => {
        lastTypingSentAtRef.current = 0
        sendMessage(JSON.stringify({ type: 'typing', isTyping: false })).catch(console.error)
      }, 3000)
    } else {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
      lastTypingSentAtRef.current = 0
      sendMessage(JSON.stringify({ type: 'typing', isTyping: false })).catch(console.error)
    }
  }

  const handleMicMouseDown = useCallback(async () => {
    if (recording) return
    try {
      const handle = await startRecording()
      recordingHandleRef.current = handle
      setRecording(true)
      setRecordingSeconds(0)
      recordingTimerRef.current = setInterval(() => setRecordingSeconds(s => s + 1), 1000)
    } catch (e) {
      console.error('Microphone access denied', e)
      alert('Could not access microphone. Please grant permission.')
    }
  }, [recording])

  const handleMicMouseUp = useCallback(async () => {
    if (!recording || !recordingHandleRef.current) return
    if (recordingTimerRef.current) clearInterval(recordingTimerRef.current)
    setRecording(false)
    setRecordingSeconds(0)
    setSending(true)
    try {
      const blob = await recordingHandleRef.current.stop()
      recordingHandleRef.current = null
      if (blob.size > 1000) { // ignore tiny accidental presses
        await sendVoiceMemo(blob)
      }
    } catch (e) {
      console.error('Failed to send voice memo', e)
      alert('Failed to send voice memo')
    } finally {
      setSending(false)
    }
  }, [recording, sendVoiceMemo])

  // Cancel recording if user drags off button
  const handleMicCancel = useCallback(() => {
    if (!recording || !recordingHandleRef.current) return
    if (recordingTimerRef.current) clearInterval(recordingTimerRef.current)
    recordingHandleRef.current.cancel()
    recordingHandleRef.current = null
    setRecording(false)
    setRecordingSeconds(0)
  }, [recording])

  const renderIncomingCallBanner = () => {
    if (incomingSignal && incomingSignal.type === 'webrtc_offer' && !activeCall) {
      return (
        <div style={{
          position: 'absolute', top: 64, left: '50%', transform: 'translateX(-50%)',
          zIndex: 100, background: 'linear-gradient(135deg, #1a1a2e, #16213e)',
          color: 'white', padding: '14px 24px', borderRadius: 24,
          display: 'flex', gap: 16, alignItems: 'center',
          boxShadow: '0 8px 40px rgba(0,0,0,0.5)',
          border: '1px solid rgba(255,255,255,0.1)',
          whiteSpace: 'nowrap',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ animation: 'pulse 1.5s infinite', color: '#22c55e' }}>
              <Phone size={18} />
            </div>
            <span style={{ fontWeight: 600 }}>
              {incomingSignal.isVoiceOnly ? 'Incoming voice call' : 'Incoming video call'}
            </span>
          </div>
          <button
            onClick={() => {
              if (activeConversationId !== incomingSignal.conversationId) {
                selectConversation(incomingSignal.conversationId)
              }
              sendMessage(`SYSTEM_UI:call_started:${incomingSignal.isVoiceOnly ? '📞 Voice' : '📹 Video'} call started`, incomingSignal.conversationId)
              setActiveCall({ isVoiceOnly: !!incomingSignal.isVoiceOnly, isIncoming: true })
              clearIncomingSignal()
            }}
            style={{ background: '#22c55e', border: 'none', padding: '8px 18px', borderRadius: 14, cursor: 'pointer', color: 'white', fontWeight: 700, fontSize: 13 }}
          >
            Answer
          </button>
          <button
            onClick={() => { sendMessage(JSON.stringify({ type: 'webrtc_call_reject' }), incomingSignal.conversationId); clearIncomingSignal() }}
            style={{ background: '#ef4444', border: 'none', padding: '8px 18px', borderRadius: 14, cursor: 'pointer', color: 'white', fontWeight: 700, fontSize: 13 }}
          >
            Decline
          </button>
        </div>
      )
    }
    return null
  }

  if (!activeConversationId) {
    return (
      <div className="chat-area">
        {renderIncomingCallBanner()}
        <div className="chat-empty">
          <span className="chat-empty-icon">🔐</span>
          <h2>Select a conversation</h2>
          <p>or add a contact to start a new encrypted chat</p>
        </div>
      </div>
    )
  }

  const clearedUpTo = activeMeta ? getClearedUpTo(activeMeta.id) : 0

  const grouped: { day: string; messages: DecodedMessage[] }[] = []
  for (const msg of messages) {
    const sentTime = ((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()).getTime()
    if (sentTime <= clearedUpTo) continue

    const day = formatDay(new Date(sentTime))
    if (!grouped.length || grouped[grouped.length - 1].day !== day) {
      grouped.push({ day, messages: [msg] })
    } else {
      grouped[grouped.length - 1].messages.push(msg)
    }
  }

  const flattenedItems = useMemo(() => {
    const items: { type: 'day' | 'message'; content: any; indexInGroup?: number }[] = []
    for (const group of grouped) {
      items.push({ type: 'day', content: group.day })
      for (let i = 0; i < group.messages.length; i++) {
        items.push({ type: 'message', content: group.messages[i], indexInGroup: i })
      }
    }
    return items
  }, [grouped])

  const virtuosoRef = useRef<VirtuosoHandle>(null)
  
  useEffect(() => {
    if (flattenedItems.length > 0) {
      setTimeout(() => {
        virtuosoRef.current?.scrollToIndex({ index: flattenedItems.length - 1, align: 'end', behavior: 'smooth' })
      }, 100)
    }
  }, [flattenedItems.length])

  const activeTypingUsers = typingUsers[activeConversationId]
  const myId = (client as any)?.inboxId || (client as any)?.address
  const isPeerTyping = activeTypingUsers
    ? Array.from(activeTypingUsers).some(id => id.toLowerCase() !== myId?.toLowerCase())
    : false

  return (
    <div className="chat-area">
      {/* Call Modal */}
      {activeCall && activeConversationId && (
        <VideoCallModal
          conversationId={activeConversationId}
          isIncoming={activeCall.isIncoming}
          isVoiceOnly={activeCall.isVoiceOnly}
          callerName={activeMeta?.displayName ?? 'Unknown'}
          onClose={(duration: number) => {
            const callType = activeCall.isVoiceOnly ? '📞 Voice call' : '📹 Video call'
            const durationStr = duration > 0
              ? ` • ${Math.floor(duration / 60)}:${(duration % 60).toString().padStart(2, '0')}`
              : ''
            sendMessage(`SYSTEM_UI:call_ended:${callType} ended${durationStr}`)
            setActiveCall(null)
          }}
        />
      )}

      {/* Global Incoming call banner */}
      {renderIncomingCallBanner()}
      <div className="chat-header" onClick={() => setShowProfile(true)} style={{ cursor: 'pointer' }}>
        {activeMeta?.profilePicture ? (
          <img src={activeMeta.profilePicture} alt="Avatar" className="avatar" style={{ width: 36, height: 36, objectFit: 'cover' }} />
        ) : (
          <div className="avatar" style={{ width: 36, height: 36, fontSize: 14 }}>
            {activeMeta?.displayName[0]?.toUpperCase() ?? '?'}
          </div>
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="chat-header-name">{activeMeta?.displayName ?? '…'}</div>
          <div className="chat-header-address">{activeMeta?.peerAddress}</div>
        </div>

        {/* Phone / call picker button */}
        <div style={{ position: 'relative' }}>
          <button
            className="icon-btn"
            style={{ padding: 8, marginRight: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(255,255,255,0.05)', borderRadius: '50%' }}
            title="Call"
            onClick={(e) => { e.stopPropagation(); setShowCallPicker(p => !p) }}
          >
            <Phone size={20} />
          </button>

          {showCallPicker && (
            <div
              style={{
                position: 'absolute', top: '110%', right: 0,
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: 14, overflow: 'hidden',
                boxShadow: '0 12px 40px rgba(0,0,0,0.35)',
                zIndex: 200, minWidth: 180,
              }}
              onClick={e => e.stopPropagation()}
            >
              <button
                style={{ width: '100%', padding: '12px 16px', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--text)', display: 'flex', alignItems: 'center', gap: 10, fontSize: 14 }}
                onMouseEnter={e => (e.currentTarget.style.background = 'var(--hover, rgba(255,255,255,0.06))')}
                onMouseLeave={e => (e.currentTarget.style.background = 'none')}
                onClick={() => {
                  setShowCallPicker(false)
                  sendMessage(`SYSTEM_UI:call_started:📞 Voice call started`)
                  setActiveCall({ isVoiceOnly: true, isIncoming: false })
                }}
              >
                <Phone size={16} /> Voice Call
              </button>
              <button
                style={{ width: '100%', padding: '12px 16px', border: 'none', background: 'none', cursor: 'pointer', color: 'var(--text)', display: 'flex', alignItems: 'center', gap: 10, fontSize: 14, borderTop: '1px solid var(--border)' }}
                onMouseEnter={e => (e.currentTarget.style.background = 'var(--hover, rgba(255,255,255,0.06))')}
                onMouseLeave={e => (e.currentTarget.style.background = 'none')}
                onClick={() => {
                  setShowCallPicker(false)
                  sendMessage(`SYSTEM_UI:call_started:📹 Video call started`)
                  setActiveCall({ isVoiceOnly: false, isIncoming: false })
                }}
              >
                <Video size={16} /> Video Call
              </button>
            </div>
          )}
        </div>

        <button className="icon-btn" style={{ padding: 8 }} title="Clear Chat History" onClick={(e) => { e.stopPropagation(); setShowClearDialog(true); }}>
          🗑️
        </button>
      </div>

      {showClearDialog && activeMeta && (
        <div className="modal-backdrop" onClick={() => setShowClearDialog(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2>Clear Chat History</h2>
            <p>How would you like to clear this chat history?</p>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
              <strong>Local Clear</strong> will instantly delete all messages from this device.<br/><br/>
              <strong>Clear for Both</strong> will send a request to the other user to consensually delete the history for both of you.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 16 }}>
              <button className="secondary-btn" onClick={() => {
                if (!activeMeta) return;
                const ts = Date.now()
                import('../services/metadataStore').then(m => {
                  m.setMetadata(activeMeta.id, { clearedUpTo: ts })
                  refreshConversations() // forces re-render which will filter out messages
                })
                setShowClearDialog(false)
              }}>Local Clear</button>
              <button className="primary-btn" onClick={() => {
                if (!activeMeta) return;
                const ts = Date.now()
                const payload = JSON.stringify({ type: 'clear_history_request', timestamp: ts })
                sendMessage(payload).catch(console.error)
                // We'll rely on the context fetching this new message shortly or we can force refresh
                setTimeout(() => refreshConversations(), 500)
                setShowClearDialog(false)
              }}>Clear for Both</button>
              <button className="icon-btn" style={{ alignSelf: 'center', marginTop: 8 }} onClick={() => setShowClearDialog(false)}>Cancel</button>
            </div>
          </div>
        </div>
      )}

      {showProfile && activeMeta && (
        <ProfileDetailsModal
          conversation={activeMeta}
          onClose={() => setShowProfile(false)}
          onProfileUpdated={() => { setShowProfile(false); refreshConversations() }}
        />
      )}

      {/* Messages */}
      <div className="messages-container">
        {messagesLoading ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="spinner" />
          </div>
        ) : (
          <>
            <Virtuoso
              ref={virtuosoRef}
              style={{ flex: 1, width: '100%', height: '100%' }}
              data={flattenedItems}
              initialTopMostItemIndex={flattenedItems.length > 0 ? flattenedItems.length - 1 : 0}
              followOutput="smooth"
              itemContent={(index, item) => {
                if (item.type === 'day') {
                  return <div className="day-divider" style={{ padding: '0 16px' }}>{item.content}</div>
                }

                const msg = item.content
                const i = item.indexInGroup!
                const isMine = ((msg as any).senderInboxId || (msg as any).senderAddress)?.toLowerCase() === myId?.toLowerCase()
                const msgId = msg.id ?? String(index)
                const msgReactions = reactions[msgId]
                const reactionCounts = msgReactions
                  ? Object.values(msgReactions).reduce<Record<string, number>>((acc, emoji) => {
                      acc[emoji] = (acc[emoji] || 0) + 1
                      return acc
                    }, {})
                  : null

                const contentText = typeof msg.content === 'string' ? msg.content : undefined

                return (
                  <div style={{ padding: '2px 16px' }}>
                  <MessageBubble
                    key={msgId}
                    msg={msg}
                    isMine={isMine}
                    msgId={msgId}
                    contentText={contentText}
                    reactionCounts={reactionCounts}
                    client={client}
                    onReact={(emoji) => sendReaction(msgId, emoji)}
                    onSystemAction={async (action) => {
                      if (!activeMeta) return
                      if (action.startsWith('accept_clear:')) {
                        const tsStr = action.split(':')[1]
                        const ts = parseInt(tsStr) || Date.now()
                        const payload = JSON.stringify({ type: 'clear_history_accept', timestamp: ts })
                        await sendMessage(payload).catch(console.error)
                        import('../services/metadataStore').then(m => {
                          m.setMetadata(activeMeta.id, { clearedUpTo: ts })
                          refreshConversations()
                        })
                      } else if (action === 'decline_clear') {
                        const payload = JSON.stringify({ type: 'clear_history_decline' })
                        await sendMessage(payload).catch(console.error)
                      }
                    }}
                  />
                  </div>
                )
              }}
            />

            {isPeerTyping && (
              <div className="message-group incoming">
                <div className="chat-bubble theirs typing-indicator">
                  <span className="dot" />
                  <span className="dot" />
                  <span className="dot" />
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </>
        )}
      </div>

      {/* Input */}
      <div className="message-input-area">
        <input type="file" ref={fileInputRef} style={{ display: 'none' }} onChange={handleAttach} />

        {/* Attach button */}
        <button
          className="attach-btn"
          onClick={() => fileInputRef.current?.click()}
          disabled={sending || recording}
          style={{ background: 'none', border: 'none', fontSize: 20, cursor: 'pointer', padding: '0 8px', color: 'var(--text)' }}
          title="Attach file"
        >
          📎
        </button>

        {/* Mic / voice memo button */}
        <button
          onMouseDown={handleMicMouseDown}
          onMouseUp={handleMicMouseUp}
          onMouseLeave={handleMicCancel}
          onTouchStart={handleMicMouseDown}
          onTouchEnd={handleMicMouseUp}
          disabled={sending && !recording}
          style={{
            background: recording ? 'var(--primary)' : 'none',
            border: 'none',
            fontSize: recording ? 14 : 20,
            cursor: 'pointer',
            padding: '0 8px',
            color: recording ? '#fff' : 'var(--text)',
            borderRadius: recording ? 12 : 0,
            transition: 'all 0.15s',
            userSelect: 'none',
            minWidth: recording ? 80 : 'auto',
          }}
          title="Hold to record voice memo"
        >
          {recording ? `🔴 ${formatDuration(recordingSeconds)}` : '🎙️'}
        </button>

        <textarea
          ref={textareaRef}
          className="message-input"
          placeholder="Message…"
          value={text}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          rows={1}
          disabled={sending || recording}
        />
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={!text.trim() || sending || recording}
          title="Send (Enter)"
        >
          {sending ? <span className="spinner dark" style={{ width: 16, height: 16 }} /> : '↑'}
        </button>
      </div>
    </div>
  )
}

// ── Message Bubble Component ─────────────────────────────────────────────────

const MessageBubble = React.memo(function MessageBubble({
  msg, isMine, msgId, contentText, reactionCounts, client, onReact, onSystemAction
}: {
  msg: DecodedMessage
  isMine: boolean
  msgId: string
  contentText?: string
  reactionCounts: Record<string, number> | null
  client?: any
  onReact: (emoji: string) => void
  onSystemAction?: (action: string) => void
}) {
  const [hovered, setHovered] = useState(false)

  const copyToClipboard = () => {
    if (contentText) navigator.clipboard.writeText(contentText).catch(console.error)
  }

  return (
    <div className={`message-group ${isMine ? 'outgoing' : 'incoming'}`}>
      <div
        style={{ position: 'relative', display: 'inline-block', maxWidth: '75%' }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        {/* Reaction toolbar on hover */}
        {hovered && (
          <ReactionToolbar
            onReact={(e) => { onReact(e); setHovered(false) }}
            onCopy={copyToClipboard}
            text={contentText}
          />
        )}

        <div className={`chat-bubble ${isMine ? 'mine' : 'theirs'} animated-message`}>
          <BubbleContent msg={msg} onSystemAction={onSystemAction} client={client} />
          <div className="chat-time">
            {formatTime(new Date(((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date())))}
          </div>
        </div>

        {/* Reaction badges */}
        {reactionCounts && Object.keys(reactionCounts).length > 0 && (
          <div style={{
            display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 4,
            justifyContent: isMine ? 'flex-end' : 'flex-start',
          }}>
            {Object.entries(reactionCounts).map(([emoji, count]) => (
              <button
                key={emoji}
                onClick={() => onReact(emoji)}
                style={{
                  background: 'var(--surface)', border: '1px solid var(--border)',
                  borderRadius: 12, padding: '2px 7px',
                  fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 3,
                }}
              >
                {emoji}{count > 1 && <span style={{ opacity: 0.8 }}>{count}</span>}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
})
