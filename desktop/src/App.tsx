import { useState } from 'react'
import { useXmtp } from './context/XmtpContext'
import RegistrationScreen from './screens/RegistrationScreen'
import ChatScreen from './screens/ChatScreen'
import Sidebar from './components/Sidebar'
import AccountModal from './components/AccountModal'

/** Custom title bar (Electron frameless window) */
function TitleBar() {
  const api = window.electronAPI
  return (
    <div className="title-bar">
      <span className="title-bar-logo">CryptoSub</span>
      {api && (
        <div className="title-bar-controls">
          <button className="title-bar-btn" onClick={api.minimize} title="Minimise">─</button>
          <button className="title-bar-btn" onClick={api.maximize} title="Maximise">□</button>
          <button className="title-bar-btn close" onClick={api.close} title="Close">✕</button>
        </div>
      )}
    </div>
  )
}

/** Main app — routing is done with simple state, no router library needed. */
export default function App() {
  const { isRegistered, isLoading } = useXmtp()
  const [showAccount, setShowAccount] = useState(false)

  // Loading state while restoring key from OS keystore
  if (isLoading) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 16, background: 'var(--bg)' }}>
        <span style={{ fontSize: 36, fontWeight: 800, letterSpacing: '0.06em' }}>CryptoSub</span>
        <span className="spinner" />
      </div>
    )
  }

  if (!isRegistered) {
    return (
      <div className="app-layout">
        <TitleBar />
        <RegistrationScreen />
      </div>
    )
  }

  return (
    <div className="app-layout">
      <TitleBar />
      <div className="main-layout">
        <Sidebar onOpenAccount={() => setShowAccount(true)} />
        <ChatScreen />
      </div>
      {showAccount && <AccountModal onClose={() => setShowAccount(false)} />}
    </div>
  )
}
