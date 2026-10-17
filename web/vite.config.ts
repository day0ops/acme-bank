/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': '/src' },
  },
  server: {
    proxy: {
      '/api/customers': { target: 'http://localhost:8081', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/customers/, '') },
      '/api/accounts': { target: 'http://localhost:8082', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/accounts/, '') },
      '/api/cards': { target: 'http://localhost:8083', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/cards/, '') },
      '/api/transactions': { target: 'http://localhost:8084', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/transactions/, '') },
      '/api/dashboard': { target: 'http://localhost:8085', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/dashboard/, '/dashboard') },
      '/api/notifications': { target: 'http://localhost:8086', changeOrigin: true, ws: true, rewrite: (path) => path.replace(/^\/api\/notifications/, '') },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.ts',
    // Playwright owns e2e/ (its own test() with a different signature);
    // Vitest must not try to collect those files too.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
