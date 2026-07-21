import { Client, DecodedMessage } from '@xmtp/browser-sdk'
import { ethers } from 'ethers'

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
  
  // Deterministically generate the local DB encryption key from the private key
  const hash = ethers.utils.sha256(wallet.privateKey)
  const dbEncryptionKey = ethers.utils.arrayify(hash)

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

  const client = await Client.create(signer, { env: 'production', dbEncryptionKey })
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
 */
export async function loadMessages(conversation: any): Promise<any[]> {
  const all = await conversation.messages()
  return all.filter((m: any) => {
    // V3 message might have text in m.content or m.content?.text depending on content type
    let contentStr = ''
    if (typeof m.content === 'string') contentStr = m.content
    else if (m.content?.text) contentStr = m.content.text
    else return false

    if (/^(@?[a-fA-F0-9]{40,}\s*)+$/.test(contentStr.trim())) return false
    return true
  }).sort((a: any, b: any) => {
    const timeA = a.sentAt || a.sent || a.createdAt
    const timeB = b.sentAt || b.sent || b.createdAt
    return new Date(timeA).getTime() - new Date(timeB).getTime()
  })
}

/**
 * Sends a text message to a conversation. Returns the message ID.
 */
export async function sendMessage(conversation: any, text: string): Promise<string> {
  const sent = await conversation.send(text)
  return typeof sent === 'string' ? sent : sent.id
}

/** System message filter — shared regex to keep behaviour in sync with Android. */
export function isSystemMessage(content: string): boolean {
  return /^(@?[a-fA-F0-9]{40,}\s*)+$/.test(content.trim())
}
