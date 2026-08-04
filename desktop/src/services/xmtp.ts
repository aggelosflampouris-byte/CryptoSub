import { Client, DecodedMessage } from '@xmtp/browser-sdk'
import { ethers } from 'ethers'
import { AttachmentCodec, RemoteAttachmentCodec, ContentTypeRemoteAttachment, RemoteAttachment } from '@xmtp/content-type-remote-attachment'
import { ATTACHMENT_UPLOAD_URL } from './constants'

/**
 * Generates a random Ethereum wallet and returns the private key hex.
 * The wallet is only used as the XMTP identity — it does not hold funds.
 */
export function generateWallet(): { privateKey: string; address: string } {
  const wallet = ethers.Wallet.createRandom()
  return {
    privateKey: wallet.privateKey.replace('0x', ''),
    address: wallet.address,
  }
}

/**
 * Creates an authenticated XMTP client for the V3 network.
 */
export async function createXmtpClient(privateKeyHex: string): Promise<Client> {
  const normalized = privateKeyHex.startsWith('0x') ? privateKeyHex : `0x${privateKeyHex}`
  const wallet = new ethers.Wallet(normalized)
  
  // Derive a 32-byte DB encryption key using PBKDF2-SHA256.
  // Using a stable, deterministic salt derived from the public address
  // so the key can be re-derived on every login without storing it.
  // PBKDF2 is used here (vs raw SHA-256) to add computational cost against brute-force.
  const saltHex = ethers.utils.id('cryptosub-xmtp-db-v1:' + wallet.address.toLowerCase())
  const saltBytes = ethers.utils.arrayify(saltHex)
  const keyBytes = ethers.utils.arrayify(wallet.privateKey)
  // Copy into a plain ArrayBuffer to satisfy WebCrypto's strict BufferSource type
  const saltBuf = new Uint8Array(saltBytes).buffer as ArrayBuffer
  const keyBuf = new Uint8Array(keyBytes).buffer as ArrayBuffer
  const subtle = (typeof window !== 'undefined' ? window : globalThis).crypto.subtle
  const baseKey = await subtle.importKey('raw', keyBuf, 'PBKDF2', false, ['deriveBits'])
  const derived = await subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt: saltBuf, iterations: 100_000 },
    baseKey,
    256
  )
  const dbEncryptionKey = new Uint8Array(derived)

  // V3 Signer Interface wrapping the ethers Wallet
  const signer = {
    type: 'EOA' as const,
    getIdentifier: async () => ({
      identifier: wallet.address,
      identifierKind: 'Ethereum'
    }) as any,
    getChainId: () => 1,
    signMessage: async (message: string | Uint8Array) => ethers.utils.arrayify(await wallet.signMessage(message))
  }

  const client = await Client.create(signer, { 
    env: 'production', 
    dbEncryptionKey,
    codecs: [new AttachmentCodec(), new RemoteAttachmentCodec()]
  } as any)
  return client
}

/**
 * Checks if an Ethereum address is registered on XMTP.
 */
export async function canMessage(client: Client, address: string): Promise<boolean> {
  try {
    const result = await client.canMessage([{
      identifier: address,
      identifierKind: 'Ethereum'
    } as any])
    return result.get(address) || false
  } catch {
    return false
  }
}

/**
 * Returns all conversations for the client, sorted by most recent activity.
 */
export async function listConversations(client: Client): Promise<any[]> {
  if ('sync' in client.conversations) {
    await (client.conversations as any).sync()
  }
  return client.conversations.list()
}

/**
 * Finds or creates a 1:1 DM conversation.
 */
export async function findOrCreateDm(client: Client, address: string): Promise<any> {
  if ('findOrCreateDm' in client.conversations) {
    return (client.conversations as any).findOrCreateDm(address)
  }
  if ('newDmWithIdentifier' in client.conversations) {
    return (client.conversations as any).newDmWithIdentifier({
      identifier: address,
      identifierKind: 'Ethereum'
    })
  }
  if ('newDm' in client.conversations) {
    return (client.conversations as any).newDm(address)
  }
  return (client.conversations as any).newConversation(address)
}

/**
 * Loads all messages for a conversation.
 * Returns the visible messages AND a pre-built reactions map from historical data.
 */
export async function loadMessages(conversation: any): Promise<{ msgs: any[]; reactions: Record<string, Record<string, string>> }> {
  if (typeof conversation.sync === 'function') {
    try {
      await conversation.sync()
    } catch (e) {
      console.error("Failed to sync conversation messages", e)
    }
  }
  const all = await conversation.messages()
  const reactions: Record<string, Record<string, string>> = {}

  const msgs = all.filter((m: any) => {
    let contentStr = ''
    if (typeof m.content === 'string') contentStr = m.content
    else if (m.content?.text) contentStr = m.content.text
    else return true // keep non-text (e.g. attachment) messages

    if (isSystemMessage(contentStr)) return false

    // Intercept structural payloads
    if (contentStr.trim().startsWith('{') && contentStr.trim().endsWith('}')) {
      try {
        const json = JSON.parse(contentStr)
        if (json.type === 'reaction') {
          // Build historical reactions map
          const senderId = (m as any).senderInboxId || (m as any).senderAddress || 'unknown'
          if (json.messageId && json.emoji) {
            reactions[json.messageId] = {
              ...(reactions[json.messageId] || {}),
              [senderId]: json.emoji,
            }
          }
          return false
        }
        if (['typing', 'read', 'reply'].includes(json.type)) return false
        if (json.type === 'reply') return true // reply messages are shown
        if (json.type && json.type.startsWith('webrtc_')) return false
      } catch (e) { }
    }

    return true
  }).sort((a: any, b: any) => {
    const timeA = a.sentAt || a.sent || a.createdAt
    const timeB = b.sentAt || b.sent || b.createdAt
    return new Date(timeA).getTime() - new Date(timeB).getTime()
  })

  return { msgs, reactions }
}

/**
 * Sends a text message to a conversation. Returns the message ID.
 */
export async function sendMessage(conversation: any, text: string): Promise<string> {
  const sent = await conversation.send(text)
  return typeof sent === 'string' ? sent : sent.id
}

function toHex(val: Uint8Array | string): string {
  if (typeof val === 'string') return val;
  return Array.from(val).map(b => b.toString(16).padStart(2, '0')).join('');
}

function getPayloadBlob(payload: Uint8Array | string): Blob {
  if (typeof payload === 'string') {
    return new Blob([new TextEncoder().encode(payload)]);
  }
  return new Blob([payload as any]);
}

function getByteLength(val: Uint8Array | string): number {
  if (typeof val === 'string') {
    return new TextEncoder().encode(val).length;
  }
  return val.length;
}

export async function sendAttachment(
  conversation: any,
  attachment: any
): Promise<string> {
  const encryptedAttachment = await RemoteAttachmentCodec.encodeEncrypted(
    attachment,
    new AttachmentCodec()
  )

  const formData = new FormData();
  formData.append('reqtype', 'fileupload');
  formData.append('fileToUpload', getPayloadBlob(encryptedAttachment.payload), attachment.filename);

  const res = await fetch(ATTACHMENT_UPLOAD_URL, {
    method: 'POST',
    body: formData
  });
  
  if (!res.ok) throw new Error('Upload failed');
  const url = (await res.text()).trim();

  const attachmentPayload = {
    type: 'attachment',
    url,
    contentDigest: toHex(encryptedAttachment.digest),
    salt: toHex(encryptedAttachment.salt),
    nonce: toHex(encryptedAttachment.nonce),
    secret: toHex(encryptedAttachment.secret),
    scheme: 'https://',
    contentLength: getByteLength(encryptedAttachment.payload),
    filename: attachment.filename
  }

  const sent = await conversation.send(JSON.stringify(attachmentPayload))
  return typeof sent === 'string' ? sent : sent.id
}

/**
 * Sends an encrypted voice memo to a conversation.
 * Identical pipeline to sendAttachment but pins the MIME type to audio/webm.
 */
export async function sendVoiceMemo(conversation: any, audioBlob: Blob): Promise<string> {
  const buffer = await audioBlob.arrayBuffer()
  const filename = `voice_memo_${Date.now()}.webm`
  const attachment = {
    filename,
    mimeType: 'audio/webm',
    data: new Uint8Array(buffer),
  }

  const encryptedAttachment = await RemoteAttachmentCodec.encodeEncrypted(
    attachment,
    new AttachmentCodec()
  )

  const formData = new FormData();
  formData.append('reqtype', 'fileupload');
  formData.append('fileToUpload', getPayloadBlob(encryptedAttachment.payload), filename);

  const res = await fetch(ATTACHMENT_UPLOAD_URL, {
    method: 'POST',
    body: formData
  });
  if (!res.ok) throw new Error('Voice memo upload failed');
  const url = (await res.text()).trim();

  const memoPayload = {
    type: 'attachment',
    url,
    contentDigest: toHex(encryptedAttachment.digest),
    salt: toHex(encryptedAttachment.salt),
    nonce: toHex(encryptedAttachment.nonce),
    secret: toHex(encryptedAttachment.secret),
    scheme: 'https://',
    contentLength: getByteLength(encryptedAttachment.payload),
    filename
  }

  const sent = await conversation.send(JSON.stringify(memoPayload))
  return typeof sent === 'string' ? sent : sent.id
}

/** System message filter — shared regex to keep behaviour in sync with Android. */
export function isSystemMessage(content: string): boolean {
  return /^(@?[a-fA-F0-9]{40,}\s*)+$/.test(content.trim())
}
