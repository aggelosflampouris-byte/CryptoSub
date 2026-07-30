export interface ConversationMetadata {
  displayName?: string
  description?: string
  profilePicture?: string // Base64 or local file path
  isHidden?: boolean
  clearedUpTo?: number
}

const STORAGE_PREFIX = 'cryptosub_meta_'

export function getMetadata(conversationId: string): ConversationMetadata {
  try {
    const raw = localStorage.getItem(`${STORAGE_PREFIX}${conversationId}`)
    if (raw) {
      return JSON.parse(raw) as ConversationMetadata
    }
  } catch (e) {
    console.error("Failed to parse metadata", e)
  }
  return {}
}

export function setMetadata(conversationId: string, data: Partial<ConversationMetadata>) {
  try {
    const existing = getMetadata(conversationId)
    const updated = { ...existing, ...data }
    localStorage.setItem(`${STORAGE_PREFIX}${conversationId}`, JSON.stringify(updated))
  } catch (e) {
    console.error("Failed to save metadata", e)
  }
}

/**
 * Returns the timestamp below which messages in a conversation have been cleared.
 * 0 means no messages have been cleared.
 */
export function getClearedUpTo(conversationId: string): number {
  return getMetadata(conversationId).clearedUpTo ?? 0
}
