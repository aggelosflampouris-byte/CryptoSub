import { useState } from 'react'
import { useXmtp } from '../context/XmtpContext'

type View = 'register' | 'import' | 'showKey'

export default function RegistrationScreen() {
  const { register, restore, isLoading, error } = useXmtp()
  const [view, setView] = useState<View>('register')
  const [generatedKey, setGeneratedKey] = useState<string | null>(null)
  const [importKey, setImportKey] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  // ── Generate new identity ────────────────────────────────────
  const handleGenerate = async () => {
    setLocalError(null)
    const key = await register()
    if (key) {
      setGeneratedKey(key)
      setView('showKey')
    }
  }

  // ── Restore from existing key ────────────────────────────────
  const handleRestore = async () => {
    setLocalError(null)
    try {
      await restore(importKey)
    } catch (e: any) {
      setLocalError(e.message)
    }
  }

  const copyKey = () => {
    if (generatedKey) {
      navigator.clipboard.writeText(generatedKey)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  // ── Show Key step ────────────────────────────────────────────
  if (view === 'showKey' && generatedKey) {
    return (
      <div className="registration-page">
        <div className="registration-bg" />
        <div className="registration-card" style={{ maxWidth: 480 }}>
          <span style={{ fontSize: 40 }}>⚠️</span>
          <h2 style={{ fontSize: 22, fontWeight: 800 }}>Crucial Security Step</h2>
          <p style={{ textAlign: 'center', color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>
            This is your Ethereum Private Key. It is the <strong>only</strong> way to access your account and
            messages if you reinstall CryptoSub.
          </p>
          <div className="key-box" style={{ width: '100%' }}>
            <span className="key-text">{generatedKey}</span>
            <button className="copy-btn" onClick={copyKey} title="Copy to clipboard">
              {copied ? '✓' : '⎘'}
            </button>
          </div>
          <p className="warning-text">
            Store it offline. DO NOT share it with anyone.
          </p>
          <button
            className="btn btn-primary"
            onClick={() => { /* Client already set in context — no action needed */ }}
            style={{ marginTop: 8 }}
          >
            ✓ I have safely stored it
          </button>
        </div>
      </div>
    )
  }

  // ── Import Key ───────────────────────────────────────────────
  if (view === 'import') {
    return (
      <div className="registration-page">
        <div className="registration-bg" />
        <div className="registration-card">
          <h2 style={{ fontSize: 22, fontWeight: 800, alignSelf: 'flex-start' }}>Import Private Key</h2>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', alignSelf: 'flex-start' }}>
            Paste the 64-character hex private key you saved during your first registration.
          </p>
          <div className="form-field" style={{ width: '100%', marginTop: 8 }}>
            <label className="form-label">Private Key (hex)</label>
            <input
              className="form-input"
              type="password"
              placeholder="64-character hex…"
              value={importKey}
              onChange={e => { setImportKey(e.target.value); setLocalError(null) }}
              autoComplete="off"
              spellCheck={false}
            />
            {(localError || error) && <p className="form-error">{localError || error}</p>}
          </div>
          <button
            className="btn btn-primary"
            onClick={handleRestore}
            disabled={isLoading || importKey.trim().length < 64}
            style={{ marginTop: 8 }}
          >
            {isLoading ? <span className="spinner dark" /> : '🔑 Restore Account'}
          </button>
          <button
            className="btn btn-ghost"
            onClick={() => { setView('register'); setLocalError(null) }}
            disabled={isLoading}
          >
            ← Back
          </button>
        </div>
      </div>
    )
  }

  // ── Main Registration Screen ─────────────────────────────────
  return (
    <div className="registration-page">
      <div className="registration-bg" />
      <div className="registration-card">
        <div className="registration-logo">CryptoSub</div>
        <p className="registration-tagline">
          A zero-knowledge identity system.<br />
          No phone number or email required.
        </p>

        {error && !view && (
          <p style={{ color: 'var(--error)', fontSize: 13, textAlign: 'center' }}>{error}</p>
        )}

        <button
          className="btn btn-primary"
          onClick={handleGenerate}
          disabled={isLoading}
          style={{ marginTop: 16 }}
        >
          {isLoading ? <span className="spinner dark" /> : <><span>🔐</span> Generate Identity</>}
        </button>

        <button
          className="btn btn-secondary"
          onClick={() => setView('import')}
          disabled={isLoading}
        >
          🔑 Import Key
        </button>

        <p style={{ fontSize: 11, color: 'var(--text-tertiary)', textAlign: 'center', marginTop: 8 }}>
          Your identity is a cryptographic key pair — no server stores your account.
        </p>
      </div>
    </div>
  )
}
