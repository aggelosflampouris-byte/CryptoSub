import { load } from '@tauri-apps/plugin-store'

/**
 * Secure key vault for the Desktop app.
 * Uses Tauri's native OS store (separate from the browser WebView) to persist
 * the user's private key. This is inaccessible to browser extensions and
 * WebView-based scripts, unlike localStorage.
 */

const STORE_FILE = 'cryptosub-vault.json'
const KEY_NAME = 'pm_pk'

async function getStore() {
  return load(STORE_FILE, { autoSave: true })
}

export async function storePrivateKey(hex: string): Promise<void> {
  const store = await getStore()
  await store.set(KEY_NAME, hex)
  await store.save()
}

export async function getPrivateKey(): Promise<string | null> {
  try {
    const store = await getStore()
    const val = await store.get<string>(KEY_NAME)
    return val ?? null
  } catch {
    return null
  }
}

export async function clearKeystore(): Promise<void> {
  const store = await getStore()
  await store.delete(KEY_NAME)
  await store.save()
}
