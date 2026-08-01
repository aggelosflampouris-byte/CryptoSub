import { useState, useEffect } from 'react'
import { useXmtp } from './context/XmtpContext'
import RegistrationScreen from './screens/RegistrationScreen'
import ChatScreen from './screens/ChatScreen'
import Sidebar from './components/Sidebar'
import AccountModal from './components/AccountModal'
import { UpdateModal } from './components/UpdateModal'
import { getSettings } from './services/settingsStore'
import { getCurrentWindow } from '@tauri-apps/api/window'

// Desktop App Entry Point

/** Main app — routing is done with simple state, no router library needed. */
export default function App() {
  const { isRegistered, isInitializing } = useXmtp()
  const [showAccount, setShowAccount] = useState(false)
  const [isLocked, setIsLocked] = useState(true)
  const [pinInput, setPinInput] = useState('')
  const [pinError, setPinError] = useState(false)
  const [failCount, setFailCount] = useState(0)
  const [cooldownUntil, setCooldownUntil] = useState(0)
  const [, setTick] = useState(0)
  const settings = getSettings()

  useEffect(() => {
    if (cooldownUntil > Date.now()) {
      const interval = setInterval(() => setTick(t => t + 1), 1000)
      return () => clearInterval(interval)
    }
  }, [cooldownUntil])

  useEffect(() => {
    if (settings.theme === 'light') document.body.classList.add('light-theme')
    else document.body.classList.remove('light-theme')
    
    if (settings.screenshotProtection) {
      getCurrentWindow().setContentProtected(true).catch(console.error)
    }

    if (!settings.appLockPin) {
      setIsLocked(false)
    }
  }, [])

  const handleUnlock = () => {
    if (cooldownUntil > Date.now()) return

    if (pinInput === settings.appLockPin) {
      setIsLocked(false)
      setFailCount(0)
    } else {
      const newFails = failCount + 1
      setFailCount(newFails)
      if (newFails >= 3) {
        setCooldownUntil(Date.now() + 5 * 60 * 1000)
        setFailCount(0)
      } else {
        setPinError(true)
      }
      setPinInput('')
    }
  }

  // Loading state while restoring key from local storage
  if (isInitializing) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 16, background: 'var(--bg)' }}>
        <span style={{ fontSize: 36, fontWeight: 800, letterSpacing: '0.06em' }}>CryptoSub</span>
        <span className="spinner" />
      </div>
    )
  }

  if (isLocked) {
    const cooldownRemaining = cooldownUntil - Date.now()
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 16, background: 'var(--bg)' }}>
        <span style={{ fontSize: 24, fontWeight: 700 }}>App Locked</span>
        {cooldownRemaining > 0 ? (
          <span style={{ color: 'var(--error)' }}>
            PIN disabled for {Math.floor(cooldownRemaining / 60000)}:
            {Math.floor((cooldownRemaining / 1000) % 60).toString().padStart(2, '0')}
          </span>
        ) : (
          <>
            <div style={{ display: 'flex', gap: 8 }}>
              <input 
                type="password" 
                className="form-input" 
                placeholder="Enter PIN" 
                value={pinInput} 
                onChange={e => { setPinInput(e.target.value); setPinError(false) }} 
                onKeyDown={e => e.key === 'Enter' && handleUnlock()}
                autoFocus
              />
              <button className="btn btn-primary" style={{ width: 'auto' }} onClick={handleUnlock}>Unlock</button>
            </div>
            {pinError && <span style={{ color: 'var(--error)', fontSize: 13 }}>Incorrect PIN</span>}
          </>
        )}
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
