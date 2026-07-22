import React, { useEffect, useState } from 'react'
import { check } from '@tauri-apps/plugin-updater'
import { relaunch } from '@tauri-apps/plugin-process'

export const UpdateModal: React.FC = () => {
  const [updateAvailable, setUpdateAvailable] = useState<any>(null)
  const [isUpdating, setIsUpdating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    // Check for updates on startup
    const checkForUpdates = async () => {
      try {
        const update = await check()
        if (update?.available) {
          setUpdateAvailable(update)
        }
      } catch (err) {
        console.error("Failed to check for updates:", err)
      }
    }
    
    // Slight delay so it doesn't block initial render
    setTimeout(checkForUpdates, 3000)
  }, [])

  const handleInstall = async () => {
    if (!updateAvailable) return
    setIsUpdating(true)
    setError(null)
    try {
      let downloaded = 0
      let contentLength = 0

      await updateAvailable.downloadAndInstall((event: any) => {
        switch (event.event) {
          case 'Started':
            contentLength = event.data.contentLength
            break
          case 'Progress':
            downloaded += event.data.chunkLength
            if (contentLength > 0) {
              setProgress(Math.round((downloaded / contentLength) * 100))
            }
            break
          case 'Finished':
            setProgress(100)
            break
        }
      })
      await relaunch()
    } catch (err: any) {
      console.error(err)
      setError(err.toString())
      setIsUpdating(false)
    }
  }

  if (!updateAvailable) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-[#1C1C1F] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl">
        <h2 className="text-xl font-bold text-white mb-2">Update Available!</h2>
        <p className="text-white/70 mb-4 text-sm">
          Version {updateAvailable.version} is available. You are currently running an older version.
        </p>

        {error && (
          <div className="p-3 mb-4 rounded-xl bg-red-500/10 text-red-400 text-sm border border-red-500/20">
            {error}
          </div>
        )}

        {isUpdating ? (
          <div className="space-y-3">
            <div className="w-full bg-white/5 rounded-full h-3 overflow-hidden border border-white/10">
              <div 
                className="bg-indigo-500 h-full transition-all duration-300 ease-out"
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-center text-sm text-white/50">Downloading... {progress}%</p>
          </div>
        ) : (
          <div className="flex gap-3 mt-6">
            <button
              onClick={() => setUpdateAvailable(null)}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white/70 hover:text-white hover:bg-white/5 transition-colors"
            >
              Skip
            </button>
            <button
              onClick={handleInstall}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white bg-indigo-500 hover:bg-indigo-600 transition-colors"
            >
              Install Update
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
