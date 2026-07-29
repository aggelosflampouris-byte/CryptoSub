import React, { useEffect, useState } from 'react'

declare const __APP_REPO__: string
const RELEASES_PAGE = `${__APP_REPO__}/releases/latest`
const VERSION_JSON  = `${__APP_REPO__}/releases/latest/download/version-desktop.json`

// Injected by Vite at build time from tauri.conf.json → version
declare const __APP_VERSION__: string
const LOCAL_BUILD = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'

function parseBuild(v: string): number {
  const parts = v.split('.')
  return parseInt(parts[parts.length - 1] ?? '0', 10) || 0
}

export const UpdateModal: React.FC = () => {
  const [remoteVersion, setRemoteVersion] = useState<string | null>(null)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    const check = async () => {
      try {
        const res = await fetch(VERSION_JSON, { method: 'GET' })
        if (!res.ok) return
        const data = await res.json() as { version: string; build: number }
        const localBuild  = parseBuild(LOCAL_BUILD)
        const remoteBuild = data.build ?? 0
        if (remoteBuild > localBuild) {
          setRemoteVersion(data.version)
        }
      } catch {
        // silently ignore — update check is non-critical
      }
    }
    // Check 4s after launch
    const t = setTimeout(check, 4000)
    // Then check every 5 minutes
    const interval = setInterval(check, 5 * 60 * 1000)
    
    return () => {
      clearTimeout(t)
      clearInterval(interval)
    }
  }, [])

  if (!remoteVersion || dismissed) return null

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(6px)', padding: '16px'
    }}>
      <div style={{
        background: '#1C1C1F', border: '1px solid rgba(255,255,255,0.1)',
        borderRadius: '20px', padding: '28px', maxWidth: '360px', width: '100%',
        boxShadow: '0 24px 64px rgba(0,0,0,0.6)'
      }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
          <span style={{ fontSize: '24px' }}>🚀</span>
          <h2 style={{ margin: 0, color: '#fff', fontSize: '20px', fontWeight: 700 }}>
            Update Available
          </h2>
        </div>

        <p style={{ color: 'rgba(255,255,255,0.65)', fontSize: '14px', lineHeight: 1.6, margin: '0 0 20px' }}>
          Version <strong style={{ color: '#fff' }}>{remoteVersion}</strong> is ready.
          You are running <strong style={{ color: '#888' }}>{LOCAL_BUILD}</strong>.
          Download the new installer and run it — your settings and keys are preserved.
        </p>

        {/* Progress bar decoration */}
        <div style={{
          height: '3px', borderRadius: '2px', background: 'rgba(255,255,255,0.06)',
          marginBottom: '20px', overflow: 'hidden'
        }}>
          <div style={{
            height: '100%', width: '60%', borderRadius: '2px',
            background: 'linear-gradient(90deg, #6366f1, #8b5cf6)'
          }} />
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={() => setDismissed(true)}
            style={{
              flex: 1, padding: '10px', borderRadius: '12px', border: 'none',
              background: 'rgba(255,255,255,0.06)', color: 'rgba(255,255,255,0.6)',
              cursor: 'pointer', fontSize: '14px', fontWeight: 500, transition: 'all 0.2s'
            }}
          >
            Later
          </button>
          <button
            onClick={() => { window.open(RELEASES_PAGE, '_blank'); setDismissed(true) }}
            style={{
              flex: 1, padding: '10px', borderRadius: '12px', border: 'none',
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              color: '#fff', cursor: 'pointer', fontSize: '14px', fontWeight: 600,
              boxShadow: '0 4px 16px rgba(99,102,241,0.4)', transition: 'all 0.2s'
            }}
          >
            Download Update ↗
          </button>
        </div>
      </div>
    </div>
  )
}
