import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

// Vite plugin to copy FontAwesome webfonts to dist
function copyFontAwesomeWebfonts() {
  return {
    name: 'copy-fontawesome-webfonts',
    closeBundle() {
      const src = path.resolve(import.meta.dirname, 'node_modules/@fortawesome/fontawesome-free/webfonts')
      const dest = path.resolve(import.meta.dirname, 'dist/webfonts')
      if (fs.existsSync(src)) {
        fs.cpSync(src, dest, { recursive: true })
      }
    },
  }
}

export default defineConfig({
  plugins: [vue(), tailwindcss(), copyFontAwesomeWebfonts()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    postcss: {},
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
      external: [],
    },
    cssCodeSplit: true,
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
