import { useEffect, useRef, useState } from 'react'
import { useXmtp } from '../context/XmtpContext'
import { DecodedMessage } from '@xmtp/browser-sdk'

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

export default function ChatScreen() {
  const { client, activeConversationId, conversations, messages, messagesLoading, sendMessage } = useXmtp()
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const activeMeta = conversations.find(c => c.id === activeConversationId)

  // Scroll to bottom when messages change
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    const trimmed = text.trim()
    if (!trimmed || sending) return
    setSending(true)
    setText('')
    try {
      await sendMessage(trimmed)
    } catch (e) {
      console.error('Send failed', e)
    } finally {
      setSending(false)
      textareaRef.current?.focus()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  // Auto-resize textarea
  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value)
    const t = e.target
    t.style.height = 'auto'
    t.style.height = `${Math.min(t.scrollHeight, 140)}px`
  }

  if (!activeConversationId) {
    return (
      <div className="chat-area">
        <div className="chat-empty">
          <span className="chat-empty-icon">🔐</span>
          <h2>Select a conversation</h2>
          <p>or add a contact to start a new encrypted chat</p>
        </div>
      </div>
    )
  }

  // Group messages by day
  const grouped: { day: string; messages: DecodedMessage[] }[] = []
  for (const msg of messages) {
    const day = formatDay(((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()))
    if (!grouped.length || grouped[grouped.length - 1].day !== day) {
      grouped.push({ day, messages: [msg] })
    } else {
      grouped[grouped.length - 1].messages.push(msg)
    }
  }

  return (
    <div className="chat-area">
      {/* Header */}
      <div className="chat-header">
        <div className="avatar" style={{ width: 36, height: 36, fontSize: 14 }}>
          {activeMeta?.displayName[0]?.toUpperCase() ?? '?'}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="chat-header-name">{activeMeta?.displayName ?? '…'}</div>
          <div className="chat-header-address">{activeMeta?.peerAddress}</div>
        </div>
      </div>

      {/* Messages */}
      <div className="messages-container">
        {messagesLoading ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="spinner" />
          </div>
        ) : (
          <>
            {grouped.map(group => (
              <div key={group.day}>
                <div className="day-divider">{group.day}</div>
                {group.messages.map((msg, i) => {
                  const isMine = ((msg as any).senderInboxId || (msg as any).senderAddress)?.toLowerCase() === ((client as any).inboxId || (client as any).address)?.toLowerCase()
                  return (
                    <div key={msg.id ?? i} className={`message-group ${isMine ? 'outgoing' : 'incoming'}`}>
                      <div className={`chat-bubble ${isMine ? 'mine' : 'theirs'}`}>
                        <div className="chat-text">{msg.content as string}</div>
                        <div className="chat-time">{formatTime(new Date(((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date())))}</div>
                      </div>
                    </div>
                  )
                })}
              </div>
            ))}
            <div ref={bottomRef} />
          </>
        )}
      </div>

      {/* Input */}
      <div className="message-input-area">
        <textarea
          ref={textareaRef}
          className="message-input"
          placeholder="Message…"
          value={text}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          rows={1}
          disabled={sending}
        />
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={!text.trim() || sending}
          title="Send (Enter)"
        >
          {sending ? <span className="spinner dark" style={{ width: 16, height: 16 }} /> : '↑'}
        </button>
      </div>
    </div>
  )
}
