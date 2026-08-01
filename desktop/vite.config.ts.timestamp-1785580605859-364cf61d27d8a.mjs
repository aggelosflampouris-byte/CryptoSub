// vite.config.ts
import { defineConfig } from "file:///C:/Users/Mr%20F/Desktop/Messenger/desktop/node_modules/vite/dist/node/index.js";
import react from "file:///C:/Users/Mr%20F/Desktop/Messenger/desktop/node_modules/@vitejs/plugin-react/dist/index.js";
import { nodePolyfills } from "file:///C:/Users/Mr%20F/Desktop/Messenger/desktop/node_modules/vite-plugin-node-polyfills/dist/index.js";
import path from "path";
import { readFileSync } from "fs";
var __vite_injected_original_dirname = "C:\\Users\\Mr F\\Desktop\\Messenger\\desktop";
var pkg = JSON.parse(readFileSync("./package.json", "utf-8"));
var vite_config_default = defineConfig(async () => ({
  plugins: [
    react(),
    nodePolyfills({
      include: ["buffer", "process", "stream", "util"],
      globals: { Buffer: true, global: true, process: true }
    })
  ],
  // Base is './' for local file loading in Tauri
  base: "./",
  resolve: {
    alias: {
      "@": path.resolve(__vite_injected_original_dirname, "./src")
    }
  },
  // Vite development server — Tauri CLI will proxy to this
  server: {
    port: 5173,
    strictPort: true,
    // Required to allow Tauri to connect
    host: "127.0.0.1"
  },
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __APP_REPO__: JSON.stringify(pkg.repository?.url?.replace("git+", "").replace(".git", "") || "https://github.com/aggelosflampouris-byte/CryptoSub")
  },
  build: {
    // Tauri expects dist/ by default
    outDir: "dist",
    // Good for debugging; minify for production
    minify: !process.env.TAURI_DEBUG ? "esbuild" : false,
    // Produce source maps only in debug mode
    sourcemap: !!process.env.TAURI_DEBUG,
    rollupOptions: {
      external: [],
      output: {
        manualChunks: {
          xmtp: ["@xmtp/browser-sdk"],
          ethers: ["ethers"]
        }
      }
    }
  }
}));
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJDOlxcXFxVc2Vyc1xcXFxNciBGXFxcXERlc2t0b3BcXFxcTWVzc2VuZ2VyXFxcXGRlc2t0b3BcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfZmlsZW5hbWUgPSBcIkM6XFxcXFVzZXJzXFxcXE1yIEZcXFxcRGVza3RvcFxcXFxNZXNzZW5nZXJcXFxcZGVza3RvcFxcXFx2aXRlLmNvbmZpZy50c1wiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9pbXBvcnRfbWV0YV91cmwgPSBcImZpbGU6Ly8vQzovVXNlcnMvTXIlMjBGL0Rlc2t0b3AvTWVzc2VuZ2VyL2Rlc2t0b3Avdml0ZS5jb25maWcudHNcIjtpbXBvcnQgeyBkZWZpbmVDb25maWcgfSBmcm9tICd2aXRlJ1xyXG5pbXBvcnQgcmVhY3QgZnJvbSAnQHZpdGVqcy9wbHVnaW4tcmVhY3QnXHJcbmltcG9ydCB7IG5vZGVQb2x5ZmlsbHMgfSBmcm9tICd2aXRlLXBsdWdpbi1ub2RlLXBvbHlmaWxscydcclxuaW1wb3J0IHBhdGggZnJvbSAncGF0aCdcclxuaW1wb3J0IHsgcmVhZEZpbGVTeW5jIH0gZnJvbSAnZnMnXHJcblxyXG5jb25zdCBwa2cgPSBKU09OLnBhcnNlKHJlYWRGaWxlU3luYygnLi9wYWNrYWdlLmpzb24nLCAndXRmLTgnKSlcclxuXHJcbi8vIGh0dHBzOi8vdml0ZWpzLmRldi9jb25maWcvXHJcbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyhhc3luYyAoKSA9PiAoe1xyXG4gIHBsdWdpbnM6IFtcclxuICAgIHJlYWN0KCksXHJcbiAgICBub2RlUG9seWZpbGxzKHtcclxuICAgICAgaW5jbHVkZTogWydidWZmZXInLCAncHJvY2VzcycsICdzdHJlYW0nLCAndXRpbCddLFxyXG4gICAgICBnbG9iYWxzOiB7IEJ1ZmZlcjogdHJ1ZSwgZ2xvYmFsOiB0cnVlLCBwcm9jZXNzOiB0cnVlIH0sXHJcbiAgICB9KVxyXG4gIF0sXHJcblxyXG4gIC8vIEJhc2UgaXMgJy4vJyBmb3IgbG9jYWwgZmlsZSBsb2FkaW5nIGluIFRhdXJpXHJcbiAgYmFzZTogJy4vJyxcclxuXHJcbiAgcmVzb2x2ZToge1xyXG4gICAgYWxpYXM6IHtcclxuICAgICAgJ0AnOiBwYXRoLnJlc29sdmUoX19kaXJuYW1lLCAnLi9zcmMnKSxcclxuICAgIH0sXHJcbiAgfSxcclxuXHJcbiAgLy8gVml0ZSBkZXZlbG9wbWVudCBzZXJ2ZXIgXHUyMDE0IFRhdXJpIENMSSB3aWxsIHByb3h5IHRvIHRoaXNcclxuICBzZXJ2ZXI6IHtcclxuICAgIHBvcnQ6IDUxNzMsXHJcbiAgICBzdHJpY3RQb3J0OiB0cnVlLFxyXG4gICAgLy8gUmVxdWlyZWQgdG8gYWxsb3cgVGF1cmkgdG8gY29ubmVjdFxyXG4gICAgaG9zdDogJzEyNy4wLjAuMScsXHJcbiAgfSxcclxuXHJcbiAgZGVmaW5lOiB7XHJcbiAgICBfX0FQUF9WRVJTSU9OX186IEpTT04uc3RyaW5naWZ5KHBrZy52ZXJzaW9uKSxcclxuICAgIF9fQVBQX1JFUE9fXzogSlNPTi5zdHJpbmdpZnkocGtnLnJlcG9zaXRvcnk/LnVybD8ucmVwbGFjZSgnZ2l0KycsICcnKS5yZXBsYWNlKCcuZ2l0JywgJycpIHx8ICdodHRwczovL2dpdGh1Yi5jb20vYWdnZWxvc2ZsYW1wb3VyaXMtYnl0ZS9DcnlwdG9TdWInKSxcclxuICB9LFxyXG5cclxuICBidWlsZDoge1xyXG4gICAgLy8gVGF1cmkgZXhwZWN0cyBkaXN0LyBieSBkZWZhdWx0XHJcbiAgICBvdXREaXI6ICdkaXN0JyxcclxuICAgIC8vIEdvb2QgZm9yIGRlYnVnZ2luZzsgbWluaWZ5IGZvciBwcm9kdWN0aW9uXHJcbiAgICBtaW5pZnk6ICFwcm9jZXNzLmVudi5UQVVSSV9ERUJVRyA/ICdlc2J1aWxkJyA6IGZhbHNlLFxyXG4gICAgLy8gUHJvZHVjZSBzb3VyY2UgbWFwcyBvbmx5IGluIGRlYnVnIG1vZGVcclxuICAgIHNvdXJjZW1hcDogISFwcm9jZXNzLmVudi5UQVVSSV9ERUJVRyxcclxuICAgIHJvbGx1cE9wdGlvbnM6IHtcclxuICAgICAgZXh0ZXJuYWw6IFtdLFxyXG4gICAgICBvdXRwdXQ6IHtcclxuICAgICAgICBtYW51YWxDaHVua3M6IHtcclxuICAgICAgICAgIHhtdHA6IFsnQHhtdHAvYnJvd3Nlci1zZGsnXSxcclxuICAgICAgICAgIGV0aGVyczogWydldGhlcnMnXSxcclxuICAgICAgICB9XHJcbiAgICAgIH1cclxuICAgIH0sXHJcbiAgfSxcclxufSkpXHJcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBcVQsU0FBUyxvQkFBb0I7QUFDbFYsT0FBTyxXQUFXO0FBQ2xCLFNBQVMscUJBQXFCO0FBQzlCLE9BQU8sVUFBVTtBQUNqQixTQUFTLG9CQUFvQjtBQUo3QixJQUFNLG1DQUFtQztBQU16QyxJQUFNLE1BQU0sS0FBSyxNQUFNLGFBQWEsa0JBQWtCLE9BQU8sQ0FBQztBQUc5RCxJQUFPLHNCQUFRLGFBQWEsYUFBYTtBQUFBLEVBQ3ZDLFNBQVM7QUFBQSxJQUNQLE1BQU07QUFBQSxJQUNOLGNBQWM7QUFBQSxNQUNaLFNBQVMsQ0FBQyxVQUFVLFdBQVcsVUFBVSxNQUFNO0FBQUEsTUFDL0MsU0FBUyxFQUFFLFFBQVEsTUFBTSxRQUFRLE1BQU0sU0FBUyxLQUFLO0FBQUEsSUFDdkQsQ0FBQztBQUFBLEVBQ0g7QUFBQTtBQUFBLEVBR0EsTUFBTTtBQUFBLEVBRU4sU0FBUztBQUFBLElBQ1AsT0FBTztBQUFBLE1BQ0wsS0FBSyxLQUFLLFFBQVEsa0NBQVcsT0FBTztBQUFBLElBQ3RDO0FBQUEsRUFDRjtBQUFBO0FBQUEsRUFHQSxRQUFRO0FBQUEsSUFDTixNQUFNO0FBQUEsSUFDTixZQUFZO0FBQUE7QUFBQSxJQUVaLE1BQU07QUFBQSxFQUNSO0FBQUEsRUFFQSxRQUFRO0FBQUEsSUFDTixpQkFBaUIsS0FBSyxVQUFVLElBQUksT0FBTztBQUFBLElBQzNDLGNBQWMsS0FBSyxVQUFVLElBQUksWUFBWSxLQUFLLFFBQVEsUUFBUSxFQUFFLEVBQUUsUUFBUSxRQUFRLEVBQUUsS0FBSyxxREFBcUQ7QUFBQSxFQUNwSjtBQUFBLEVBRUEsT0FBTztBQUFBO0FBQUEsSUFFTCxRQUFRO0FBQUE7QUFBQSxJQUVSLFFBQVEsQ0FBQyxRQUFRLElBQUksY0FBYyxZQUFZO0FBQUE7QUFBQSxJQUUvQyxXQUFXLENBQUMsQ0FBQyxRQUFRLElBQUk7QUFBQSxJQUN6QixlQUFlO0FBQUEsTUFDYixVQUFVLENBQUM7QUFBQSxNQUNYLFFBQVE7QUFBQSxRQUNOLGNBQWM7QUFBQSxVQUNaLE1BQU0sQ0FBQyxtQkFBbUI7QUFBQSxVQUMxQixRQUFRLENBQUMsUUFBUTtBQUFBLFFBQ25CO0FBQUEsTUFDRjtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQ0YsRUFBRTsiLAogICJuYW1lcyI6IFtdCn0K
