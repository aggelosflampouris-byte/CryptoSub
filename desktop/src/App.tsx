import { useState } from 'react'
import { useXmtp } from './context/XmtpContext'
import RegistrationScreen from './screens/RegistrationScreen'
import ChatScreen from './screens/ChatScreen'
import Sidebar from './components/Sidebar'
import AccountModal from './components/AccountModal'
import { UpdateModal } from './components/UpdateModal'

/** Main app — routing is done with simple state, no router library needed. */
export default function App() {
  const { isRegistered, isLoading } = useXmtp()
  const [showAccount, setShowAccount] = useState(false)

  // Loading state while restoring key from local storage
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
        <RegistrationScreen />
      </div>
    )
  }

  return (
    <div className="app-layout">
      <UpdateModal />
      <div className="main-layout">
        <Sidebar onOpenAccount={() => setShowAccount(true)} />
        <ChatScreen />
      </div>
      {showAccount && <AccountModal onClose={() => setShowAccount(false)} />}
    </div>
  )
}
