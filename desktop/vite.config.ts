import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  base: './',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
  },
  optimizeDeps: {
    // XMTP JS has some Node.js-specific modules that need polyfills
    exclude: ['@xmtp/xmtp-js'],
    esbuildOptions: {
      define: {
        global: 'globalThis',
      },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      external: [],
    },
  },
})
