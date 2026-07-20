/**
 * Secure key vault for the Web App.
 * Uses localStorage to persist the user's private key across sessions.
 * In a production web3 environment, this could optionally be encrypted with a user PIN.
 */

export async function storePrivateKey(hex: string): Promise<void> {
  localStorage.setItem('pm_pk', hex)
}

export async function getPrivateKey(): Promise<string | null> {
  return localStorage.getItem('pm_pk')
}

export async function clearKeystore(): Promise<void> {
  localStorage.removeItem('pm_pk')
}
