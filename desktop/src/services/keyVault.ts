import { load, Store } from '@tauri-apps/plugin-store'

/**
 * Secure key vault for the Desktop app.
 * Uses Tauri's native OS store (separate from the browser WebView) to persist
 * the user's private key. This is inaccessible to browser extensions and
 * WebView-based scripts, unlike localStorage.
 */

const STORE_FILE = 'cryptosub-vault.json'
const KEY_NAME = 'pm_pk'

let storePromise: Promise<Store> | null = null

async function getStore() {
  if (!storePromise) {
    storePromise = load(STORE_FILE, { autoSave: true })
  }
  return storePromise
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
