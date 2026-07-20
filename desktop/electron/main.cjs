// Electron main process — runs in Node.js, creates the OS window
const { app, BrowserWindow, ipcMain, nativeTheme } = require('electron')
const path = require('path')
const isDev = !app.isPackaged

// Security: Force dark mode to match app aesthetic
nativeTheme.themeSource = 'dark'

let mainWindow

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 750,
    minWidth: 800,
    minHeight: 600,
    frame: false,           // Custom frameless window — we draw our own title bar
    transparent: false,
    backgroundColor: '#000000',
    titleBarStyle: 'hidden',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,  // Security: renderer cannot directly access Node
      sandbox: false,          // Needed for XMTP SDK
      webSecurity: !isDev,
    },
    icon: path.join(__dirname, '../public/icon.ico'),
  })

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools({ mode: 'detach' })
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }
}

// ── Window control IPC handlers (for custom title bar) ──────────────────────
ipcMain.on('window-minimize', () => mainWindow?.minimize())
ipcMain.on('window-maximize', () => {
  if (mainWindow?.isMaximized()) mainWindow.unmaximize()
  else mainWindow?.maximize()
})
ipcMain.on('window-close', () => mainWindow?.close())

// ── Secure key storage using Electron's safeStorage ─────────────────────────
const { safeStorage } = require('electron')
const fs = require('fs')
const storePath = path.join(app.getPath('userData'), 'cs_keystore.enc')

ipcMain.handle('keystore-get', () => {
  try {
    if (!fs.existsSync(storePath)) return null
    const encrypted = fs.readFileSync(storePath)
    return safeStorage.decryptString(encrypted)
  } catch { return null }
})

ipcMain.handle('keystore-set', (_event, value) => {
  try {
    const encrypted = safeStorage.encryptString(value)
    fs.writeFileSync(storePath, encrypted)
    return true
  } catch { return false }
})

ipcMain.handle('keystore-clear', () => {
  try {
    if (fs.existsSync(storePath)) fs.unlinkSync(storePath)
    return true
  } catch { return false }
})

// ── App lifecycle ────────────────────────────────────────────────────────────
app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow()
})
