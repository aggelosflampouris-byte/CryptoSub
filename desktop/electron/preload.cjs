// Electron preload — runs in a privileged context, bridges IPC to the renderer
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  // Window controls (custom title bar)
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close:    () => ipcRenderer.send('window-close'),

  // Secure OS-level key storage (uses OS keychain under the hood)
  keystoreGet:   ()              => ipcRenderer.invoke('keystore-get'),
  keystoreSet:   (value: string) => ipcRenderer.invoke('keystore-set', value),
  keystoreClear: ()              => ipcRenderer.invoke('keystore-clear'),
})
