import { Client, Conversation, DecodedMessage } from '@xmtp/browser-sdk'
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { isPermissionGranted, requestPermission, sendNotification } from '@tauri-apps/plugin-notification'
import { clearKeystore, getPrivateKey, storePrivateKey } from '../services/keyVault'
import {
  createXmtpClient,
  findOrCreateDm,
  generateWallet,
  isSystemMessage,
  listConversations,
  loadMessages,
  sendMessage as xmtpSendMessage,
  sendVoiceMemo as xmtpSendVoiceMemo,
} from '../services/xmtp'
import { getMetadata } from '../services/metadataStore'

// msgId -> { senderId -> emoji }
export type ReactionsMap = Record<string, Record<string, string>>

// ── Types ─────────────────────────────────────────────────────────────────────

export interface ConversationMeta {
  id: string
  topic: string
  peerAddress: string // For groups, this will just be the group ID
  displayName: string
  lastMessage: string
  lastMessageTs: number
  unreadCount: number
  isGroup: boolean
  profilePicture?: string
  description?: string
}

interface XmtpContextValue {
  client: Client | null
  isConnected: boolean
  isRegistered: boolean
  isLoading: boolean
  error: string | null
  conversations: ConversationMeta[]
  activeConversationId: string | null
  messages: DecodedMessage[]
  messagesLoading: boolean
  typingUsers: { [conversationId: string]: Set<string> }
  reactions: ReactionsMap
  register: () => Promise<string | null>
  restore: (privateKeyHex: string) => Promise<void>
  logout: () => Promise<void>
  selectConversation: (id: string) => void
  startNewConversation: (address: string) => Promise<void>
  sendMessage: (text: string) => Promise<void>
  sendAttachment: (file: File) => Promise<void>
  sendReaction: (messageId: string, emoji: string) => Promise<void>
  sendVoiceMemo: (audioBlob: Blob) => Promise<void>
  refreshConversations: () => Promise<void>
}

const XmtpContext = createContext<XmtpContextValue | null>(null)

// ── Provider ──────────────────────────────────────────────────────────────────

export function XmtpProvider({ children }: { children: React.ReactNode }) {
  const [client, setClient] = useState<Client | null>(null)
  const [isConnected, setIsConnected] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [conversations, setConversations] = useState<ConversationMeta[]>([])
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<DecodedMessage[]>([])
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [typingUsers, setTypingUsers] = useState<{ [convId: string]: Set<string> }>({})
  const [reactions, setReactions] = useState<ReactionsMap>({})

  const activeConvRef = useRef<string | null>(null)
  const convMapRef = useRef<Map<string, Conversation>>(new Map())
  const streamRef = useRef<AsyncGenerator | null>(null)

  // ── Helpers ──────────────────────────────────────────────────────────────

  const buildMeta = useCallback(async (conv: any): Promise<ConversationMeta> => {
    if (typeof conv.sync === 'function') {
      try { await conv.sync() } catch (e) { console.error("Sync failed for buildMeta", e) }
    }
    const msgs = await conv.messages({ limit: 1n })
    const last = msgs.length > 0 ? msgs[msgs.length - 1] : null
    const lastText = last && typeof last.content === 'string' && !isSystemMessage(last.content)
      ? last.content
      : ''
    let peerId = 'unknown'
    let isGroup = false
    let display = 'Unknown'
    
    // XMTP v3 Group vs Dm detection
    // DMs in v3 have peerInboxId(), DMs in v2 have peerAddress
    if (typeof conv.peerInboxId !== 'function' && typeof conv.peerAddress !== 'string') {
      isGroup = true
      display = conv.name || 'Unnamed Group'
      peerId = conv.id // groups don't have a single peer
    } else {
      if (conv.peerAddress && typeof conv.peerAddress === 'string') {
        peerId = conv.peerAddress
      } else if (typeof conv.peerInboxId === 'function') {
        try {
          peerId = await conv.peerInboxId()
        } catch (e) {
          console.error("Failed to get peerInboxId", e)
        }
      } else if (typeof conv.peerInboxId === 'string') {
        peerId = conv.peerInboxId
      }
      display = peerId === 'unknown' ? 'Unknown' : `${peerId.slice(0, 6)}…${peerId.slice(-4)}`
    }

    const metadata = getMetadata(conv.id)
    if (metadata.displayName) {
      display = metadata.displayName
    }

    return {
      id: conv.id,
      topic: conv.id,
      peerAddress: peerId,
      displayName: display,
      lastMessage: lastText,
      lastMessageTs: last ? ((last as any).sentAt || (last as any).sent || (last as any).createdAt || new Date()).getTime() : 0,
      unreadCount: 0,
      isGroup,
      profilePicture: metadata.profilePicture,
      description: metadata.description
    }
  }, [])

  const loadConversations = useCallback(async (xmtpClient: Client) => {
    const convs = await listConversations(xmtpClient)
    const metas = await Promise.all(convs.map(async c => {
      convMapRef.current.set(c.id, c)
      return buildMeta(c)
    }))
    setConversations(metas.sort((a, b) => b.lastMessageTs - a.lastMessageTs))
  }, [buildMeta])

  // ── Streaming ─────────────────────────────────────────────────────────────

  const startStreaming = useCallback(async (xmtpClient: Client) => {
    if (streamRef.current) return
    
    // We run the stream in an async IIFE so we don't block
    ;(async () => {
      let retryDelay = 3000
      let isActive = true
      
      // Store a cleanup function to allow logging out
      streamRef.current = { return: () => { isActive = false } } as any

      while (isActive) {
        try {
          // Sync first to catch anything missed while disconnected
          await xmtpClient.conversations.sync().catch(console.error)
          
          const stream = await xmtpClient.conversations.streamAllMessages()
          setIsConnected(true)
          retryDelay = 3000 // reset on success

          for await (const msg of stream) {
            if (!isActive) break
            if (!msg) continue
        
        let contentStr = ''
        if (typeof msg.content === 'string') contentStr = msg.content
        else if ((msg.content as any)?.text) contentStr = (msg.content as any).text
        else continue
        
        if (isSystemMessage(contentStr)) continue

        let isStructural = false
        if (contentStr.startsWith('{') && contentStr.endsWith('}')) {
          try {
            const json = JSON.parse(contentStr)
            if (json.type === 'typing') {
              isStructural = true
              const convIdStr = (msg as any).conversationId || (msg as any).conversation?.id || (msg as any).conversationTopic || (msg as any).topic || (msg as any).conversation?.topic
              const senderIdStr = (msg as any).senderInboxId || (msg as any).senderAddress
              if (convIdStr && senderIdStr) {
                setTypingUsers(prev => {
                  const currentSet = new Set(prev[convIdStr] || [])
                  if (json.isTyping) currentSet.add(senderIdStr)
                  else currentSet.delete(senderIdStr)
                  
                  const next = { ...prev }
                  if (currentSet.size === 0) delete next[convIdStr]
                  else next[convIdStr] = currentSet
                  return next
                })
              }
            } else if (json.type === 'reaction') {
              isStructural = true
              const senderId = (msg as any).senderInboxId || (msg as any).senderAddress || 'unknown'
              if (json.messageId && json.emoji) {
                setReactions(prev => ({
                  ...prev,
                  [json.messageId]: {
                    ...(prev[json.messageId] || {}),
                    [senderId]: json.emoji,
                  }
                }))
              }
            } else if (json.type === 'read') {
              isStructural = true
            } else if (json.type === 'reply') {
              contentStr = json.content || contentStr
            }
          } catch(e) {}
        }
        
        if (isStructural) continue

        const convId = (msg as any).conversationId || (msg as any).conversation?.id || (msg as any).conversationTopic || (msg as any).topic || (msg as any).conversation?.topic

        if (!convId) {
          console.warn("Message missing conversation identifier", msg)
          continue
        }

        if (!convMapRef.current.has(convId)) {
          // New conversation! Reload the list in the background
          loadConversations(xmtpClient).catch(console.error)
        }

        const senderId = (msg as any).senderInboxId || (msg as any).senderAddress
        const myId = (xmtpClient as any).inboxId || (xmtpClient as any).address
        const isFromPeer = senderId?.toLowerCase() !== myId?.toLowerCase()
        const isActiveChat = activeConvRef.current === convId
        let shouldNotify = false
        let notifyLabel = 'New Message'

      // Update conversation list
      setConversations(prev => {
        const idx = prev.findIndex(c => c.id === convId)
        if (idx < 0) {
          // If we haven't loaded it yet, just return prev. loadConversations will get it soon.
          return prev 
        }

        const updated = { ...prev[idx] }
        updated.lastMessage = contentStr
        updated.lastMessageTs = ((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()).getTime()

        if (isFromPeer) {
          if (!isActiveChat) {
            updated.unreadCount = (updated.unreadCount || 0) + 1
            shouldNotify = true
            notifyLabel = updated.displayName || 'New Message'
          } else {
            updated.unreadCount = 0
          }
        }

        const next = [...prev]
        next[idx] = updated
        return next.sort((a, b) => b.lastMessageTs - a.lastMessageTs)
      })

      if (shouldNotify) {
        try {
          let permissionGranted = await isPermissionGranted()
          if (!permissionGranted) {
            const permission = await requestPermission()
            permissionGranted = permission === 'granted'
          }
          if (permissionGranted) {
            sendNotification({ title: notifyLabel, body: contentStr })
          }
        } catch (e) {
          console.error('Failed to send notification', e)
        }
      }

      setActiveConversationId(activeId => {
        const msgConvId = (msg as any).conversationId || (msg as any).conversation?.id || (msg as any).conversationTopic || (msg as any).topic || (msg as any).conversation?.topic
        if (activeId === msgConvId) {
          setMessages(prev => {
            if (prev.some(m => m.id === msg.id)) return prev
            return [...prev, msg]
          })
          }
          return activeId
        })
      }
      
      if (isActive) {
        setIsConnected(false)
        await new Promise(r => setTimeout(r, retryDelay))
      }
      
      } catch (e) {
        setIsConnected(false)
        console.error("Message stream failed, restarting...", e)
        if (isActive) {
          await new Promise(r => setTimeout(r, retryDelay))
          retryDelay = Math.min(retryDelay * 2, 30000)
        }
      }
    }
    })()
  }, [loadConversations])

  const startConversationStream = useCallback(async (xmtpClient: Client) => {
    let isActive = true
    ;(async () => {
      let retryDelay = 3000
      while (isActive) {
        try {
          const stream = await xmtpClient.conversations.stream()
          retryDelay = 3000
          for await (const conv of stream) {
            if (!isActive) break
            if (!conv) continue
            if (!convMapRef.current.has(conv.id)) {
              loadConversations(xmtpClient).catch(console.error)
              
              // New contact notification
              let permissionGranted = await isPermissionGranted()
              if (!permissionGranted) {
                const permission = await requestPermission()
                permissionGranted = permission === 'granted'
              }
              if (permissionGranted) {
                sendNotification({ title: 'New Contact', body: 'Someone has started a conversation with you on CryptoSub.' })
              }
            }
          }
          if (isActive) await new Promise(r => setTimeout(r, retryDelay))
        } catch (e) {
          console.error("Conversation stream failed, restarting...", e)
          if (isActive) {
            await new Promise(r => setTimeout(r, retryDelay))
            retryDelay = Math.min(retryDelay * 2, 30000)
          }
        }
      }
    })()
    
    // Return a function to stop the loop when logging out
    return () => { isActive = false }
  }, [loadConversations])

  // ── Auth ──────────────────────────────────────────────────────────────────

  const initClient = useCallback(async (privateKeyHex: string) => {
    const xmtpClient = await createXmtpClient(privateKeyHex)
    setClient(xmtpClient)
    await loadConversations(xmtpClient)
    startStreaming(xmtpClient)
    startConversationStream(xmtpClient)
    return xmtpClient
  }, [loadConversations, startStreaming, startConversationStream])

  // Restore session on startup
  const initRef = useRef(false)
  useEffect(() => {
    if (initRef.current) return
    initRef.current = true
    
    ;(async () => {
      try {
        const stored = await getPrivateKey()
        if (stored) await initClient(stored)
      } catch (e) {
        console.error('Failed to restore XMTP session', e)
      } finally {
        setIsLoading(false)
      }
    })()
  }, [initClient])

  // Background Sync polling — fallback for when the Waku stream drops.
  // Interval is 30 s (not 5 s) to avoid scanning all conversations on every tick.
  useEffect(() => {
    if (!client) return
    const interval = setInterval(async () => {
      try {
        await client.conversations.sync()
        // Reload the conversation list in case the stream missed a new contact
        await loadConversations(client)
        if (activeConversationId) {
          const conv = convMapRef.current.get(activeConversationId)
          if (conv) {
             const { msgs } = await loadMessages(conv)
             setMessages(msgs)
          }
        }
      } catch (e) {
        console.error('Poll failed:', e)
      }
    }, 30_000)
    return () => clearInterval(interval)
  }, [client, activeConversationId, loadConversations])

  const register = useCallback(async (): Promise<string | null> => {
    setIsLoading(true)
    setError(null)
    try {
      const { privateKey } = generateWallet()
      await storePrivateKey(privateKey)
      await initClient(privateKey)
      return privateKey
    } catch (e: any) {
      setError(e.message || 'Failed to generate identity.')
      return null
    } finally {
      setIsLoading(false)
    }
  }, [initClient])

  const restore = useCallback(async (privateKeyHex: string) => {
    const clean = privateKeyHex.trim().replace(/^0x/i, '')
    if (clean.length !== 64 || !/^[0-9a-fA-F]+$/.test(clean)) {
      throw new Error('Invalid private key. Must be 64 hex characters (32 bytes).')
    }
    setIsLoading(true)
    setError(null)
    try {
      await storePrivateKey(clean)
      await initClient(clean)
    } catch (e: any) {
      await clearKeystore()
      throw new Error(e.message || 'Failed to restore identity.')
    } finally {
      setIsLoading(false)
    }
  }, [initClient])

  const logout = useCallback(async () => {
    streamRef.current = null
    setClient(null)
    setConversations([])
    setMessages([])
    setActiveConversationId(null)
    activeConvRef.current = null
    convMapRef.current.clear()
    await clearKeystore()
  }, [])

  // ── Conversation Actions ──────────────────────────────────────────────────

  const selectConversation = useCallback((id: string) => {
    setActiveConversationId(id)
    activeConvRef.current = id
    // Clear unread when opening
    setConversations(prev => prev.map(c => c.id === id ? { ...c, unreadCount: 0 } : c))
    // Load messages and reactions
    const conv = convMapRef.current.get(id)
    if (!conv) return
    setMessagesLoading(true)
    loadMessages(conv).then(({ msgs, reactions: newReactions }) => {
      setMessages(msgs)
      setReactions(prev => ({ ...prev, ...newReactions }))
      setMessagesLoading(false)
    })
  }, [])

  const startNewConversation = useCallback(async (address: string) => {
    if (!client) throw new Error('Not connected')
    const conv = await findOrCreateDm(client, address)
    convMapRef.current.set(conv.id, conv)
    const meta = await buildMeta(conv)
    setConversations(prev => {
      const exists = prev.find(c => c.id === conv.id)
      if (exists) return prev
      return [meta, ...prev]
    })
    
    // Restart message stream so we receive replies in this new conversation
    if (streamRef.current) {
      try { (streamRef.current as any).return?.() } catch(e) {}
      streamRef.current = null
    }
    startStreaming(client)
    
    selectConversation(conv.id)
  }, [client, selectConversation, buildMeta, startStreaming])

  const sendMessage = useCallback(async (text: string) => {
    if (!client || !activeConversationId) return
    const conv = convMapRef.current.get(activeConversationId)
    if (!conv) return
    await xmtpSendMessage(conv, text)
  }, [client, activeConversationId])

  const sendAttachment = useCallback(async (file: File) => {
    if (!client || !activeConversationId) return
    const conv = convMapRef.current.get(activeConversationId)
    if (!conv) return
    const { sendAttachment: xmtpSendAttachment } = await import('../services/xmtp')
    await xmtpSendAttachment(conv, file)
  }, [client, activeConversationId])

  const sendReaction = useCallback(async (messageId: string, emoji: string) => {
    if (!client || !activeConversationId) return
    const conv = convMapRef.current.get(activeConversationId)
    if (!conv) return
    await xmtpSendMessage(conv, JSON.stringify({ type: 'reaction', messageId, emoji }))
    // Optimistic local update
    const myId = (client as any).inboxId || (client as any).address
    setReactions(prev => ({
      ...prev,
      [messageId]: { ...(prev[messageId] || {}), [myId]: emoji }
    }))
  }, [client, activeConversationId])

  const sendVoiceMemo = useCallback(async (audioBlob: Blob) => {
    if (!client || !activeConversationId) return
    const conv = convMapRef.current.get(activeConversationId)
    if (!conv) return
    await xmtpSendVoiceMemo(conv, audioBlob)
  }, [client, activeConversationId])

  const refreshConversations = useCallback(async () => {
    if (!client) return
    await loadConversations(client)
  }, [client, loadConversations])

  return (
    <XmtpContext.Provider value={{
      client,
      isConnected,
      isRegistered: !!client,
      isLoading,
      error,
      conversations,
      activeConversationId,
      messages,
      messagesLoading,
      typingUsers,
      reactions,
      register,
      restore,
      logout,
      selectConversation,
      startNewConversation,
      sendMessage,
      sendAttachment,
      sendReaction,
      sendVoiceMemo,
      refreshConversations,
    }}>
      {children}
    </XmtpContext.Provider>
  )
}

export function useXmtp() {
  const ctx = useContext(XmtpContext)
  if (!ctx) throw new Error('useXmtp must be used inside XmtpProvider')
  return ctx
}
