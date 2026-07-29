import { useState, useEffect } from 'react'
import { useXmtp } from '../context/XmtpContext'
import { getPrivateKey } from '../services/keyVault'
import { getVersion } from '@tauri-apps/api/app'
import { fetch } from '@tauri-apps/plugin-http'
import { QRCodeSVG } from 'qrcode.react'
import { getSettings, updateSettings, Theme, AppSettings } from '../services/settingsStore'
import { getCurrentWindow } from '@tauri-apps/api/window'

declare const __APP_REPO__: string
const RELEASES_PAGE = typeof __APP_REPO__ !== 'undefined' ? `${__APP_REPO__}/releases/latest` : 'https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest'
const VERSION_JSON  = typeof __APP_REPO__ !== 'undefined' ? `${__APP_REPO__}/releases/latest/download/version-desktop.json` : 'https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest/download/version-desktop.json'

declare const __APP_VERSION__: string
const LOCAL_BUILD = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'

function parseBuild(v: string): number {
  const parts = v.split('.')
  return parseInt(parts[parts.length - 1] ?? '0', 10) || 0
}

interface Props {
  onClose: () => void
}

type Tab = 'account' | 'privacy' | 'appearance'

export default function AccountModal({ onClose }: Props) {
  const { client, logout } = useXmtp()
  const [activeTab, setActiveTab] = useState<Tab>('account')
  
  // Account Tab State
  const [privateKey, setPrivateKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState(false)
  const [copied, setCopied] = useState(false)
  const [confirmLogout, setConfirmLogout] = useState(false)
  const [understandRisks, setUnderstandRisks] = useState(false)
  const [appVersion, setAppVersion] = useState<string>('')
  const [updateAvailable, setUpdateAvailable] = useState<boolean>(false)
  const [checkingUpdate, setCheckingUpdate] = useState<boolean>(false)

  // Settings State
  const [settings, setSettings] = useState<AppSettings>(getSettings())
  const [setupPin, setSetupPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [showPinSetup, setShowPinSetup] = useState(false)
  const [pinError, setPinError] = useState('')

  useEffect(() => {
    getVersion().then(setAppVersion).catch(() => setAppVersion(LOCAL_BUILD))
    fetch(VERSION_JSON, { method: 'GET' })
      .then(res => res.json())
      .then((data: any) => {
        if ((data.build ?? 0) > parseBuild(LOCAL_BUILD)) setUpdateAvailable(true)
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

  const publicAddress = ((client as any)?.inboxId || (client as any)?.address) ?? ''

  const handleUpdateSetting = async (updates: Partial<AppSettings>) => {
    const next = updateSettings(updates)
    setSettings(next)
    
    if (updates.screenshotProtection !== undefined) {
      try {
        await getCurrentWindow().setContentProtected(updates.screenshotProtection)
      } catch (e) {
        console.error("Failed to set content protection", e)
      }
    }
    
    if (updates.theme !== undefined) {
      if (updates.theme === 'light') document.body.classList.add('light-theme')
      else document.body.classList.remove('light-theme')
    }
  }

  const handleSetupPin = () => {
    if (setupPin.length < 4) {
      setPinError('PIN must be at least 4 digits')
      return
    }
    if (setupPin !== confirmPin) {
      setPinError('PINs do not match')
      return
    }
    handleUpdateSetting({ appLockPin: setupPin })
    setShowPinSetup(false)
    setSetupPin('')
    setConfirmPin('')
    setPinError('')
  }

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal" style={{ width: 500, maxHeight: '90vh', overflow: 'hidden', padding: 0 }}>
        
        {/* Header & Tabs */}
        <div style={{ padding: '24px 32px 0 32px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h2 className="modal-title" style={{ margin: 0 }}>Settings</h2>
            <button className="icon-btn" onClick={onClose} style={{ fontSize: 18 }}>×</button>
          </div>
          <div style={{ display: 'flex', gap: 20, borderBottom: '1px solid var(--border)' }}>
            {(['account', 'privacy', 'appearance'] as Tab[]).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                style={{
                  background: 'none',
                  border: 'none',
                  padding: '12px 4px',
                  color: activeTab === tab ? 'var(--primary)' : 'var(--text-secondary)',
                  fontWeight: activeTab === tab ? 700 : 500,
                  borderBottom: activeTab === tab ? '2px solid var(--primary)' : '2px solid transparent',
                  cursor: 'pointer',
                  textTransform: 'capitalize'
                }}
              >
                {tab}
              </button>
            ))}
          </div>
        </div>

        {/* Scrollable Content */}
        <div style={{ overflowY: 'auto', padding: '24px 32px', display: 'flex', flexDirection: 'column', gap: 20, maxHeight: 'calc(90vh - 120px)' }}>
          
          {/* ─── ACCOUNT TAB ─── */}
          {activeTab === 'account' && (
            <>
              <div className="section-card" style={{ gap: 16, alignItems: 'center' }}>
                <span className="section-title" style={{ alignSelf: 'flex-start' }}>Share Profile</span>
                <div style={{ background: '#fff', padding: 16, borderRadius: 8, display: 'inline-block' }}>
                  <QRCodeSVG value={`ethereum:${publicAddress}`} size={160} />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Public Address</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, wordBreak: 'break-all', flex: 1, color: 'var(--text-primary)' }}>
                      {publicAddress || '—'}
                    </span>
                    <button className="copy-btn" onClick={() => handleCopy(publicAddress)} title="Copy address">
                      {copied ? '✓' : '⎘'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="section-card" style={{ gap: 10 }}>
                <span className="section-title">Private Key Backup</span>
                {!showKey ? (
                  <>
                    <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                      Reveal and securely store your private key. Required to restore your account.
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
                    <p className="warning-text" style={{ fontSize: 12 }}>Store this offline. Never share it.</p>
                  </>
                )}
              </div>

              <div className="section-card" style={{ gap: 10 }}>
                <span className="section-title">Danger Zone</span>
                {!confirmLogout ? (
                  <button className="btn btn-ghost" style={{ borderColor: 'var(--error)', color: 'var(--error)' }} onClick={() => setConfirmLogout(true)}>
                    🚪 Logout & Clear Keys
                  </button>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <p style={{ fontSize: 13, color: 'var(--error)', fontWeight: 600 }}>
                      WARNING: You will lose access permanently unless you have backed up your private key!
                    </p>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer', color: 'var(--text-primary)' }}>
                      <input type="checkbox" checked={understandRisks} onChange={e => setUnderstandRisks(e.target.checked)} />
                      I understand the risks
                    </label>
                    <div style={{ display: 'flex', gap: 10 }}>
                      <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => { setConfirmLogout(false); setUnderstandRisks(false); }}>Cancel</button>
                      <button className="btn" style={{ flex: 1, background: understandRisks ? 'var(--error)' : 'var(--surface-variant)', color: understandRisks ? '#fff' : 'var(--text-secondary)' }} disabled={!understandRisks} onClick={async () => { await logout(); onClose() }}>Confirm</button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}

          {/* ─── PRIVACY TAB ─── */}
          {activeTab === 'privacy' && (
            <>
              <div className="section-card" style={{ gap: 16 }}>
                <span className="section-title">Security</span>
                
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>Screenshot Protection</span>
                    <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Block screenshots and screen recording</span>
                  </div>
                  <label className="switch">
                    <input type="checkbox" checked={settings.screenshotProtection} onChange={e => handleUpdateSetting({ screenshotProtection: e.target.checked })} />
                    <span className="slider round"></span>
                  </label>
                </div>
                
                <div style={{ width: '100%', height: 1, background: 'var(--border)' }} />
                
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>App Lock PIN</span>
                    <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Require PIN to open the app</span>
                  </div>
                  {settings.appLockPin ? (
                    <button className="btn btn-secondary" style={{ width: 'auto', height: 32, fontSize: 12, padding: '0 12px' }} onClick={() => handleUpdateSetting({ appLockPin: null })}>
                      Remove PIN
                    </button>
                  ) : (
                    <button className="btn btn-primary" style={{ width: 'auto', height: 32, fontSize: 12, padding: '0 12px' }} onClick={() => setShowPinSetup(true)}>
                      Set PIN
                    </button>
                  )}
                </div>

                {showPinSetup && !settings.appLockPin && (
                  <div style={{ background: 'var(--surface-variant)', padding: 16, borderRadius: 8, display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <input type="password" placeholder="Enter 4+ digit PIN" className="form-input" value={setupPin} onChange={e => setSetupPin(e.target.value)} maxLength={8} />
                    <input type="password" placeholder="Confirm PIN" className="form-input" value={confirmPin} onChange={e => setConfirmPin(e.target.value)} maxLength={8} />
                    {pinError && <span style={{ color: 'var(--error)', fontSize: 12 }}>{pinError}</span>}
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button className="btn btn-ghost" style={{ flex: 1, height: 36 }} onClick={() => { setShowPinSetup(false); setPinError('') }}>Cancel</button>
                      <button className="btn btn-primary" style={{ flex: 1, height: 36 }} onClick={handleSetupPin}>Save</button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}

          {/* ─── APPEARANCE TAB ─── */}
          {activeTab === 'appearance' && (
            <>
              <div className="section-card" style={{ gap: 16 }}>
                <span className="section-title">Theme</span>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {(['system', 'dark', 'light'] as Theme[]).map(t => (
                    <label key={t} style={{ display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer', padding: '8px 0' }}>
                      <input 
                        type="radio" 
                        name="theme" 
                        checked={settings.theme === t} 
                        onChange={() => handleUpdateSetting({ theme: t })}
                        style={{ accentColor: 'var(--primary)', width: 18, height: 18 }}
                      />
                      <span style={{ fontSize: 15, textTransform: 'capitalize' }}>{t}</span>
                    </label>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* Version Footer */}
          <div style={{ textAlign: 'center', fontSize: 12, color: 'var(--text-tertiary)', marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
            <span>App Version: {appVersion || 'Loading...'}</span>
            {updateAvailable ? (
              <button onClick={() => window.open(RELEASES_PAGE, '_blank')} style={{ background: 'var(--primary)', color: '#000', border: 'none', borderRadius: 4, padding: '2px 8px', fontSize: 11, cursor: 'pointer', fontWeight: 'bold' }}>
                Update Available
              </button>
            ) : (
              <button onClick={async () => {
                setCheckingUpdate(true)
                try {
                  const res = await fetch(VERSION_JSON, { method: 'GET' })
                  const data = await res.json()
                  if ((data.build ?? 0) > parseBuild(LOCAL_BUILD)) setUpdateAvailable(true)
                  else alert('You are on the latest version.')
                } catch { alert('Failed to check for updates.') }
                finally { setCheckingUpdate(false) }
              }} style={{ background: 'var(--surface-variant)', color: 'var(--text-secondary)', border: 'none', borderRadius: 4, padding: '2px 8px', fontSize: 11, cursor: 'pointer' }} disabled={checkingUpdate}>
                {checkingUpdate ? 'Checking...' : 'Check for Updates'}
              </button>
            )}
          </div>

        </div>
      </div>
    </div>
  )
}
