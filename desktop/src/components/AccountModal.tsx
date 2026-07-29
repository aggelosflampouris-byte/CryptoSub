import { useState, useEffect } from 'react'
import { useXmtp } from '../context/XmtpContext'
import { getPrivateKey } from '../services/keyVault'
import { getVersion } from '@tauri-apps/api/app'
import { fetch } from '@tauri-apps/plugin-http'

declare const __APP_REPO__: string
const RELEASES_PAGE = typeof __APP_REPO__ !== 'undefined' ? `${__APP_REPO__}/releases/latest` : 'https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest'
const VERSION_JSON  = typeof __APP_REPO__ !== 'undefined' ? `${__APP_REPO__}/releases/latest/download/version-desktop.json` : 'https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest/download/version-desktop.json'

// Injected by Vite at build time
declare const __APP_VERSION__: string
const LOCAL_BUILD = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'

function parseBuild(v: string): number {
  const parts = v.split('.')
  return parseInt(parts[parts.length - 1] ?? '0', 10) || 0
}

interface Props {
  onClose: () => void
}

export default function AccountModal({ onClose }: Props) {
  const { client, logout } = useXmtp()
  const [privateKey, setPrivateKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState(false)
  const [copied, setCopied] = useState(false)
  const [confirmLogout, setConfirmLogout] = useState(false)
  const [understandRisks, setUnderstandRisks] = useState(false)
  const [appVersion, setAppVersion] = useState<string>('')
  const [updateAvailable, setUpdateAvailable] = useState<boolean>(false)
  const [checkingUpdate, setCheckingUpdate] = useState<boolean>(false)

  useEffect(() => {
    getVersion().then(setAppVersion).catch(() => setAppVersion(LOCAL_BUILD))
    
    // Check for update silently
    fetch(VERSION_JSON, { method: 'GET' })
      .then(res => res.json())
      .then((data: any) => {
        const localBuild = parseBuild(LOCAL_BUILD)
        if ((data.build ?? 0) > localBuild) setUpdateAvailable(true)
      }).catch(() => {})
  }, [])

  const handleRevealKey = async () => {
    const key = await getPrivateKey()
    setPrivateKey(key)
    setShowKey(true)
  }

  const handleCopy = (value: string) => {
    navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal" style={{ width: 460 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="modal-title">Account</h2>
          <button className="icon-btn" onClick={onClose} style={{ fontSize: 18 }}>×</button>
        </div>

        {/* Identity */}
        <div className="section-card" style={{ gap: 10 }}>
          <span className="section-title">Your Identity</span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>XMTP Inbox / Ethereum Address</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, wordBreak: 'break-all', flex: 1, color: 'var(--text-primary)' }}>
                {((client as any)?.inboxId || (client as any)?.address) ?? '—'}
              </span>
              <button className="copy-btn" onClick={() => handleCopy(((client as any)?.inboxId || (client as any)?.address) ?? '')} title="Copy address">
                {copied ? '✓' : '⎘'}
              </button>
            </div>
          </div>
        </div>

        {/* Private Key */}
        <div className="section-card" style={{ gap: 10 }}>
          <span className="section-title">Private Key Backup</span>
          {!showKey ? (
            <>
              <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                Reveal and securely store your private key. It is required to restore your account on any device.
              </p>
              <button className="btn btn-secondary" onClick={handleRevealKey}>
                🔑 Reveal Private Key
              </button>
            </>
          ) : (
            <>
              <div className="key-box">
                <span className="key-text">{privateKey}</span>
                <button className="copy-btn" onClick={() => handleCopy(privateKey ?? '')} title="Copy">⎘</button>
              </div>
              <p className="warning-text" style={{ fontSize: 12 }}>
                Store this offline. Never share it.
              </p>
            </>
          )}
        </div>

        {/* Logout */}
        <div className="section-card" style={{ gap: 10 }}>
          <span className="section-title">Danger Zone</span>
          {!confirmLogout ? (
            <button className="btn btn-ghost" style={{ borderColor: 'var(--error)', color: 'var(--error)' }} onClick={() => setConfirmLogout(true)}>
              🚪 Logout & Clear Keys
            </button>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <p style={{ fontSize: 13, color: 'var(--error)', fontWeight: 600 }}>
                WARNING: Logging out will permanently delete your keys from this device. If you haven't backed up your private key, you will lose access to all your encrypted messages forever. There is no way to recover them.
              </p>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer', color: 'var(--text-primary)' }}>
                <input type="checkbox" checked={understandRisks} onChange={e => setUnderstandRisks(e.target.checked)} />
                I understand the risks
              </label>
              <div style={{ display: 'flex', gap: 10 }}>
                <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => { setConfirmLogout(false); setUnderstandRisks(false); }}>Cancel</button>
                <button
                  className="btn"
                  style={{ flex: 1, background: understandRisks ? 'var(--error)' : 'var(--surface-variant)', color: understandRisks ? '#fff' : 'var(--text-secondary)' }}
                  disabled={!understandRisks}
                  onClick={async () => { await logout(); onClose() }}
                >
                  Confirm Logout
                </button>
              </div>
            </div>
          )}
        </div>

        {/* App Version */}
        <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--text-tertiary)', marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
          <span>App Version: {appVersion || 'Loading...'} (In Development)</span>
          {updateAvailable ? (
            <button 
              onClick={() => window.open(RELEASES_PAGE, '_blank')}
              style={{ background: 'var(--primary-color)', color: 'white', border: 'none', borderRadius: '4px', padding: '2px 8px', fontSize: '11px', cursor: 'pointer', fontWeight: 'bold' }}
            >
              Update Available
            </button>
          ) : (
            <button 
              onClick={async () => {
                setCheckingUpdate(true)
                try {
                  const res = await fetch(VERSION_JSON, { method: 'GET' })
                  const data = await res.json()
                  if ((data.build ?? 0) > parseBuild(LOCAL_BUILD)) {
                    setUpdateAvailable(true)
                  } else {
                    alert('You are on the latest version.')
                  }
                } catch (e: any) {
                  console.error(e)
                  alert('Failed to check for updates.')
                } finally {
                  setCheckingUpdate(false)
                }
              }}
              style={{ background: 'var(--surface-variant)', color: 'var(--text-secondary)', border: 'none', borderRadius: '4px', padding: '2px 8px', fontSize: '11px', cursor: 'pointer' }}
              disabled={checkingUpdate}
            >
              {checkingUpdate ? 'Checking...' : 'Check for Updates'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
