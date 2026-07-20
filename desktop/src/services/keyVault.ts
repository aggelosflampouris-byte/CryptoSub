/**
 * Secure key vault — wraps Electron's OS-level safeStorage via IPC.
 * On Windows, safeStorage uses DPAPI (Data Protection API) which ties
 * encryption to the current Windows user account — equivalent to Android Keystore.
 */

declare global {
  interface Window {
    electronAPI: {
      minimize: () => void
      maximize: () => void
      close: () => void
      keystoreGet: () => Promise<string | null>
      keystoreSet: (value: string) => Promise<boolean>
      keystoreClear: () => Promise<boolean>
    }
  }
}

/**
 * Stores the Ethereum private key securely using Windows DPAPI via Electron.
 * Falls back to sessionStorage for browser-based testing.
 */
export async function storePrivateKey(hex: string): Promise<void> {
  if (window.electronAPI) {
    await window.electronAPI.keystoreSet(hex)
  } else {
    sessionStorage.setItem('pm_pk', hex)
  }
}

/**
 * Retrieves the stored private key, or null if not set.
 */
export async function getPrivateKey(): Promise<string | null> {
  if (window.electronAPI) {
    return window.electronAPI.keystoreGet()
  }
  return sessionStorage.getItem('pm_pk')
}

/**
 * Clears all stored keys (logout / wipe).
 */
export async function clearKeystore(): Promise<void> {
  if (window.electronAPI) {
    await window.electronAPI.keystoreClear()
  } else {
    sessionStorage.clear()
  }
}
