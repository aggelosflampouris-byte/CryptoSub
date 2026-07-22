import { Client, Conversation, DecodedMessage } from '@xmtp/browser-sdk'
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { clearKeystore, getPrivateKey, storePrivateKey } from '../services/keyVault'
import {
  createXmtpClient,
  findOrCreateDm,
  generateWallet,
  isSystemMessage,
  listConversations,
  loadMessages,
  sendMessage as xmtpSendMessage,
} from '../services/xmtp'

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
}

interface XmtpContextValue {
  client: Client | null
  isRegistered: boolean
  isLoading: boolean
  error: string | null
  conversations: ConversationMeta[]
  activeConversationId: string | null
  messages: DecodedMessage[]
  messagesLoading: boolean
  register: () => Promise<string | null>
  restore: (privateKeyHex: string) => Promise<void>
  logout: () => Promise<void>
  selectConversation: (id: string) => void
  startNewConversation: (address: string) => Promise<void>
  sendMessage: (text: string) => Promise<void>
  refreshConversations: () => Promise<void>
}

const XmtpContext = createContext<XmtpContextValue | null>(null)

// ── Provider ──────────────────────────────────────────────────────────────────

export function XmtpProvider({ children }: { children: React.ReactNode }) {
  const [client, setClient] = useState<Client | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [conversations, setConversations] = useState<ConversationMeta[]>([])
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<DecodedMessage[]>([])
  const [messagesLoading, setMessagesLoading] = useState(false)

  const convMapRef = useRef<Map<string, Conversation>>(new Map())
  const streamRef = useRef<AsyncGenerator | null>(null)

  // ── Helpers ──────────────────────────────────────────────────────────────

  const buildMeta = useCallback(async (conv: any): Promise<ConversationMeta> => {
    const msgs = await conv.messages({ limit: 1n })
    const last = msgs.length > 0 ? msgs[msgs.length - 1] : null
    const lastText = last && typeof last.content === 'string' && !isSystemMessage(last.content)
      ? last.content
      : ''
    let peerId = 'unknown'
    let isGroup = false
    let display = 'Unknown'
    
    if (typeof conv.members === 'function' || typeof conv.listMembers === 'function' || 'admins' in conv || 'name' in conv) {
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

    return {
      id: conv.id,
      topic: conv.id,
      peerAddress: peerId,
      displayName: display,
      lastMessage: lastText,
      lastMessageTs: last ? ((last as any).sentAt || (last as any).sent || (last as any).createdAt || new Date()).getTime() : 0,
      unreadCount: 0,
      isGroup
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
    const stream = await xmtpClient.conversations.streamAllMessages()
    streamRef.current = stream as any
    for await (const msg of stream) {
      if (!msg) continue
      
      let contentStr = ''
      if (typeof msg.content === 'string') contentStr = msg.content
      else if ((msg.content as any)?.text) contentStr = (msg.content as any).text
      else continue
      
      if (isSystemMessage(contentStr)) continue

      const convId = (msg as any).conversationId

      if (!convMapRef.current.has(convId)) {
        // New conversation! Reload the list in the background
        loadConversations(xmtpClient).catch(console.error)
      }

      // Update conversation list
      setConversations(prev => {
        const idx = prev.findIndex(c => c.id === convId)
        if (idx < 0) return prev // Wait for loadConversations to populate it

        const updated = { ...prev[idx] }
        updated.lastMessage = contentStr
        updated.lastMessageTs = ((msg as any).sentAt || (msg as any).sent || (msg as any).createdAt || new Date()).getTime()

        // Only increment unread if message is from the peer
        const senderId = (msg as any).senderInboxId || (msg as any).senderAddress
        const myId = (xmtpClient as any).inboxId || (xmtpClient as any).address
        if (senderId?.toLowerCase() !== myId?.toLowerCase()) {
          updated.unreadCount = (updated.unreadCount || 0) + 1
        }

        setActiveConversationId(activeId => {
          if (activeId === convId && senderId?.toLowerCase() !== myId?.toLowerCase()) {
            updated.unreadCount = 0 // Clear if the chat is open
          }
          return activeId
        })

        const next = [...prev]
        next[idx] = updated
        return next.sort((a, b) => b.lastMessageTs - a.lastMessageTs)
      })

      setActiveConversationId(activeId => {
        if (activeId === (msg as any).conversationId) {
          setMessages(prev => [...prev, msg])
        }
        return activeId
      })
    }
  }, [loadConversations])

  const startConversationStream = useCallback(async (xmtpClient: Client) => {
    try {
      const stream = await xmtpClient.conversations.stream()
      for await (const conv of stream) {
        if (!convMapRef.current.has(conv.id)) {
          loadConversations(xmtpClient).catch(console.error)
          
          // Restart the message stream to include the new conversation's topic
          if (streamRef.current) {
            try { (streamRef.current as any).return?.() } catch(e) {}
            streamRef.current = null
          }
          startStreaming(xmtpClient)
        }
      }
    } catch (e) {
      console.error("Conversation stream failed", e)
    }
  }, [loadConversations, startStreaming])

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
  useEffect(() => {
    (async () => {
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
    convMapRef.current.clear()
    await clearKeystore()
  }, [])

  // ── Conversation Actions ──────────────────────────────────────────────────

  const selectConversation = useCallback((id: string) => {
    setActiveConversationId(id)
    // Clear unread when opening
    setConversations(prev => prev.map(c => c.id === id ? { ...c, unreadCount: 0 } : c))
    // Load messages
    const conv = convMapRef.current.get(id)
    if (!conv) return
    setMessagesLoading(true)
    loadMessages(conv).then(msgs => {
      setMessages(msgs)
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

  const refreshConversations = useCallback(async () => {
    if (!client) return
    await loadConversations(client)
  }, [client, loadConversations])

  return (
    <XmtpContext.Provider value={{
      client,
      isRegistered: !!client,
      isLoading,
      error,
      conversations,
      activeConversationId,
      messages,
      messagesLoading,
      register,
      restore,
      logout,
      selectConversation,
      startNewConversation,
      sendMessage,
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
