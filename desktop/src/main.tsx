import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import { XmtpProvider } from './context/XmtpContext.tsx'
import { fetch as tauriFetch } from '@tauri-apps/plugin-http'
import './index.css'

// ── Global CORS Bypass for Catbox ──
// Catbox.moe does not send Access-Control-Allow-Origin headers.
// This causes browser window.fetch to fail when reading responses (both upload and download).
// We intercept fetch and route catbox requests through Tauri's native fetch which ignores CORS.
const originalFetch = window.fetch
window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
  const urlStr = typeof input === 'string' ? input : (input instanceof URL ? input.href : (input as Request).url)
  if (urlStr.includes('catbox.moe')) {
    try {
      // @ts-ignore - tauriFetch implements the fetch API but typing might differ slightly
      return await tauriFetch(input, init)
    } catch (e) {
      console.error('Tauri fetch error for catbox:', e)
      throw e
    }
  }
  return originalFetch(input, init)
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <XmtpProvider>
      <App />
    </XmtpProvider>
  </React.StrictMode>
)
