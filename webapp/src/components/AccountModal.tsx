import { useState } from 'react'
import { useXmtp } from '../context/XmtpContext'
import { getPrivateKey } from '../services/keyVault'

interface Props {
  onClose: () => void
}

export default function AccountModal({ onClose }: Props) {
  const { client, logout } = useXmtp()
  const [privateKey, setPrivateKey] = useState<string | null>(null)
  const [showKey, setShowKey] = useState(false)
  const [copied, setCopied] = useState(false)
  const [confirmLogout, setConfirmLogout] = useState(false)

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
                {client?.address ?? '—'}
              </span>
              <button className="copy-btn" onClick={() => handleCopy(client?.address ?? '')} title="Copy address">
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
                This will permanently remove your keys from this device. Make sure you have your private key backed up first!
              </p>
              <div style={{ display: 'flex', gap: 10 }}>
                <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setConfirmLogout(false)}>Cancel</button>
                <button
                  className="btn"
                  style={{ flex: 1, background: 'var(--error)', color: '#fff' }}
                  onClick={async () => { await logout(); onClose() }}
                >
                  Confirm Logout
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
