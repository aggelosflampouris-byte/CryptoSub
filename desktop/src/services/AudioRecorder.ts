/**
 * Thin wrapper around the browser MediaRecorder API.
 * Records to audio/webm (Opus codec) — natively supported in Chromium/WebKit
 * webviews used by Tauri.
 */
export interface RecordingHandle {
  /** Stops recording and resolves with the captured audio Blob */
  stop: () => Promise<Blob>
  /** Cancels recording without returning data */
  cancel: () => void
}

export async function startRecording(): Promise<RecordingHandle> {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })

  const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
    ? 'audio/webm;codecs=opus'
    : 'audio/webm'

  const recorder = new MediaRecorder(stream, { mimeType })
  const chunks: BlobPart[] = []

  recorder.addEventListener('dataavailable', (e) => {
    if (e.data.size > 0) chunks.push(e.data)
  })

  recorder.start(100) // collect 100ms chunks for low-latency

  const stopTracks = () => stream.getTracks().forEach(t => t.stop())

  return {
    stop: () =>
      new Promise<Blob>((resolve) => {
        recorder.addEventListener('stop', () => {
          stopTracks()
          resolve(new Blob(chunks, { type: mimeType }))
        }, { once: true })
        recorder.stop()
      }),
    cancel: () => {
      recorder.stop()
      stopTracks()
    },
  }
}
