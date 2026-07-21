import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { useXmtp } from '../context/XmtpContext'
import { canMessage } from '../services/xmtp'

interface Props {
  onClose: () => void
}

export default function AddContactModal({ onClose }: Props) {
  const { client, startNewConversation } = useXmtp()
  const [tab, setTab] = useState<'address' | 'qr'>('address')
  const [address, setAddress] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)
  const myAddress = (client as any)?.accountIdentifier || (client as any)?.inboxId || (client as any)?.address || ''

  // Generate our own QR code on mount
  useEffect(() => {
    if (myAddress) {
      QRCode.toDataURL(myAddress, { width: 220, margin: 1, color: { dark: '#000', light: '#fff' } })
        .then(setQrDataUrl)
    }
  }, [myAddress])

  const handleAdd = async () => {
    setError(null)
    const clean = address.trim()
    if (!clean.match(/^0x[a-fA-F0-9]{40}$/)) {
      setError('Invalid Ethereum address. Must be 0x followed by 40 hex characters.')
      return
    }
    if (!client) return
    setLoading(true)
    try {
      const reachable = await canMessage(client, clean)
      if (!reachable) {
        setError('This address has not registered on XMTP yet.')
        return
      }
      await startNewConversation(clean)
      onClose()
    } catch (e: any) {
      setError(e.message || 'Failed to start conversation.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 className="modal-title">Add Contact</h2>
          <button className="icon-btn" onClick={onClose} style={{ fontSize: 18 }}>×</button>
        </div>

        {/* Tab switcher */}
        <div style={{ display: 'flex', gap: 8, background: 'var(--surface-variant)', borderRadius: 'var(--radius-md)', padding: 4 }}>
          {(['address', 'qr'] as const).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              style={{
                flex: 1, height: 34, border: 'none', borderRadius: 10, cursor: 'pointer',
                background: tab === t ? 'var(--border-bright)' : 'transparent',
                color: tab === t ? 'var(--text-primary)' : 'var(--text-secondary)',
                fontWeight: 600, fontSize: 13, transition: 'background 150ms',
              }}
            >
              {t === 'address' ? '📋 Enter Address' : '🔲 My QR Code'}
            </button>
          ))}
        </div>

        {tab === 'address' ? (
          <div className="flex-col gap-12">
            <div className="form-field">
              <label className="form-label">Ethereum Address</label>
              <input
                className="form-input"
                placeholder="0x…"
                value={address}
                onChange={e => { setAddress(e.target.value); setError(null) }}
                autoFocus
                spellCheck={false}
              />
              {error && <p className="form-error">{error}</p>}
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" style={{ width: 'auto', padding: '0 20px' }} onClick={onClose}>Cancel</button>
              <button
                className="btn btn-primary"
                style={{ width: 'auto', padding: '0 20px' }}
                onClick={handleAdd}
                disabled={loading || !address.trim()}
              >
                {loading ? <span className="spinner dark" /> : 'Start Chat'}
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-col align-center gap-12">
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', textAlign: 'center' }}>
              Let others scan this code to message you securely.
            </p>
            {qrDataUrl ? (
              <div className="qr-container">
                <img src={qrDataUrl} alt="Your XMTP QR code" width={220} height={220} />
              </div>
            ) : (
              <div style={{ width: 220, height: 220, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span className="spinner" />
              </div>
            )}
            <div style={{ background: 'var(--surface-variant)', borderRadius: 'var(--radius-md)', padding: '10px 14px', width: '100%' }}>
              <p style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--text-secondary)', wordBreak: 'break-all' }}>
                {myAddress}
              </p>
            </div>
            <button
              className="btn btn-ghost"
              onClick={() => { navigator.clipboard.writeText(myAddress) }}
              style={{ fontSize: 13 }}
            >
              ⎘ Copy Address
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
