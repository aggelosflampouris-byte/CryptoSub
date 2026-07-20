import { Client, Conversation, DecodedMessage, SortDirection } from '@xmtp/xmtp-js'
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
 * Creates an authenticated XMTP client from a private key hex string.
 * Connects to the XMTP production network (V2).
 */
export async function createXmtpClient(privateKeyHex: string): Promise<Client> {
  const normalized = privateKeyHex.startsWith('0x') ? privateKeyHex : `0x${privateKeyHex}`
  const wallet = new ethers.Wallet(normalized)
  const client = await Client.create(wallet, { env: 'production' })
  return client
}

/**
 * Checks if an Ethereum address is registered on XMTP.
 */
export async function canMessage(client: Client, address: string): Promise<boolean> {
  try {
    return await client.canMessage(address)
  } catch {
    return false
  }
}

/**
 * Returns all conversations for the client, sorted by most recent activity.
 */
export async function listConversations(client: Client): Promise<Conversation[]> {
  return client.conversations.list()
}

/**
 * Finds or creates a 1:1 DM conversation with the given Ethereum address.
 */
export async function findOrCreateDm(client: Client, address: string): Promise<Conversation> {
  return client.conversations.newConversation(address)
}

/**
 * Loads all messages for a conversation.
 * Filters out XMTP system messages (raw public key payloads) the same way
 * the Android app does using a regex check.
 */
export async function loadMessages(conversation: Conversation): Promise<DecodedMessage[]> {
  const all = await conversation.messages({ direction: SortDirection.SORT_DIRECTION_ASCENDING })
  return all.filter(m => {
    if (typeof m.content !== 'string') return false
    // Filter out XMTP system messages (inbox ID / public key blob patterns)
    if (/^(@?[a-fA-F0-9]{40,}\s*)+$/.test(m.content.trim())) return false
    return true
  })
}

/**
 * Sends a text message to a conversation. Returns the message ID.
 */
export async function sendMessage(conversation: Conversation, text: string): Promise<string> {
  const sent = await conversation.send(text)
  return sent
}

/** System message filter — shared regex to keep behaviour in sync with Android. */
export function isSystemMessage(content: string): boolean {
  return /^(@?[a-fA-F0-9]{40,}\s*)+$/.test(content.trim())
}
