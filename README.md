# CryptoSub 🛡️

> **Secure, zero-knowledge, peer-to-peer encrypted messenger — no phone number, no email, no server.**  
> Powered by **XMTP V3** · End-to-End Encrypted · Fully decentralised

---

## 📥 Download & Install

### 🖥️ Desktop App (Windows · macOS · Linux)

| Platform | Download |
|---|---|
| 🪟 **Windows** | [⬇️ Download `.exe` installer](https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest) |
| 🍎 **macOS** | [⬇️ Download `.dmg`](https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest) |
| 🐧 **Linux** | [⬇️ Download `.AppImage` / `.deb`](https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest) |

> 🔄 The desktop app is **automatically built and published** on every update — the link above always points to the latest version.

**First-time install notes:**
- **Windows:** If SmartScreen warns about an unknown publisher, click *"More info → Run anyway."*
- **macOS:** If Gatekeeper blocks the app, right-click the `.dmg` and choose *Open.*
- **Linux:** Make the AppImage executable: `chmod +x CryptoSub*.AppImage && ./CryptoSub*.AppImage`

---

### 📱 Android App (APK)

| Platform | Download |
|---|---|
| 🤖 **Android** | [⬇️ Download Latest APK](https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest) |

> ⚠️ You may need to enable *"Install from Unknown Sources"* in Android Settings → Security before installing.

---

## 🔒 How It Works

CryptoSub is built on **XMTP V3 (MLS)** — a fully decentralised messaging protocol:

- **No accounts, no servers:** Your identity is a cryptographic key pair you own entirely.
- **End-to-End Encrypted:** Messages are encrypted before they leave your device. Nobody can read them — not even us.
- **Zero metadata:** No phone numbers, no emails, no centralized databases.
- **Cross-platform:** The Android app and Desktop app share the same cryptographic identity — one key, both devices.

---

## 🏗️ Build from Source

### Desktop App (`/desktop`)
```bash
# Requires: Node.js 20+, Rust stable, and platform WebView (pre-installed on Windows/macOS)
cd desktop
npm install
npm run tauri:dev      # Run in development mode (native window)
npm run tauri:build    # Build the production installer
```

### Android App (`/android`)
```bash
# Requires: Android Studio, Android SDK 26+
# Open /android in Android Studio, sync Gradle, and run on device.
```

---

## ⚙️ Tech Stack

| Layer | Technology |
|---|---|
| **Protocol** | [XMTP V3 / MLS](https://xmtp.org/) |
| **Desktop Frontend** | React 18, Vite, TypeScript |
| **Desktop Shell** | [Tauri v2](https://tauri.app/) (Rust + native OS WebView) |
| **Mobile** | Kotlin, Jetpack Compose, XMTP Android SDK |
| **Encryption** | WebCrypto API (browser), BouncyCastle (Android) |

---

## 🔑 Security Notice

> **Save your private key!** CryptoSub generates a local Ethereum key pair as your identity.  
> There are **no accounts to recover.** If you lose your key, you lose access to your messages permanently.  
> Store it securely — use a password manager or write it down offline.
