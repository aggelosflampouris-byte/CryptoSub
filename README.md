# CryptoSub 🛡️

CryptoSub is a secure, zero-knowledge privacy messenger leveraging the state-of-the-art **XMTP V3** protocol. 
It requires absolutely no phone numbers, no emails, and no centralized databases. Your identity is simply a cryptographic key pair, giving you complete ownership and privacy over your communications.

## 🚀 Access the App

We provide two primary ways to access and use CryptoSub:

### 1. Web App (Recommended)
Experience CryptoSub instantly in your browser. The web app is fully responsive and offers all the features of the native client.
- **[Launch Web App](https://aggelosflampouris-byte.github.io/CryptoSub/)**

### 2. Android Native App (APK)
For a native mobile experience with background notifications, you can download the latest Android build.
- **[Download Latest APK](https://github.com/aggelosflampouris-byte/CryptoSub/releases/latest)**

*Note: When downloading the APK from GitHub Releases, you may need to allow installation from "Unknown Sources" in your Android settings.*

---

## 🏗️ Technical Details

CryptoSub is built with modern, secure technologies:
- **Protocol:** [XMTP V3](https://xmtp.org/) (Extensible Message Transport Protocol)
- **Encryption:** End-to-End Encryption (E2EE) by default.
- **Web App:** React, Vite, TypeScript, and `@xmtp/browser-sdk` utilizing WebAssembly (WASM) for high-performance cryptographic operations in the browser.
- **Android App:** Native Kotlin, Jetpack Compose, Coroutines, and the XMTP Kotlin SDK.

### Build Instructions

**For the Web App (`/webapp`):**
```bash
cd webapp
npm install
npm run dev      # Start local development server
npm run build    # Build for production
```

**For the Android App (`/android`):**
- Open the `/android` folder in Android Studio.
- Sync Gradle.
- Run the app on a connected device or emulator.
*(Alternatively, run `./gradlew assembleDebug` from the command line).*

---

## 🔒 Security & Privacy

Your chats are entirely off-grid from traditional servers. 
- **No Centralized Accounts:** You generate your own Ethereum-based private key offline.
- **Save Your Key:** Because there are no centralized accounts, losing your private key means losing access to your identity and messages permanently. **Always store your private key securely offline.**
