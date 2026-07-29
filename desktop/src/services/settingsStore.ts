export type Theme = 'system' | 'light' | 'dark'

export interface AppSettings {
  theme: Theme
  screenshotProtection: boolean
  appLockPin: string | null
}

const STORAGE_KEY = 'cryptosub_settings'

const defaultSettings: AppSettings = {
  theme: 'system',
  screenshotProtection: false,
  appLockPin: null,
}

export function getSettings(): AppSettings {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (!stored) return defaultSettings
  try {
    return { ...defaultSettings, ...JSON.parse(stored) }
  } catch {
    return defaultSettings
  }
}

export function updateSettings(updates: Partial<AppSettings>): AppSettings {
  const current = getSettings()
  const next = { ...current, ...updates }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}
