import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { nodePolyfills } from 'vite-plugin-node-polyfills'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(async () => ({
  plugins: [
    react(),
    nodePolyfills({
      include: ['buffer', 'process', 'stream', 'util'],
      globals: { Buffer: true, global: true, process: true },
    })
  ],

  // Base is './' for local file loading in Tauri
  base: './',

  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  // Vite development server — Tauri CLI will proxy to this
  server: {
    port: 5173,
    strictPort: true,
    // Required to allow Tauri to connect
    host: '127.0.0.1',
  },

  build: {
    // Tauri expects dist/ by default
    outDir: 'dist',
    // Good for debugging; minify for production
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    // Produce source maps only in debug mode
    sourcemap: !!process.env.TAURI_DEBUG,
    rollupOptions: {
      external: [],
    },
  },
}))
