/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // The harness assigns a port when 5173 is taken (Docker Desktop squats on it here).
    port: process.env.PORT ? Number(process.env.PORT) : 5173,
    // Proxy /api to the local backend so the browser sees a same-origin request in development.
    // This keeps cookie-based auth (DEC-032) working locally without relaxing SameSite.
    // In production the two are genuinely cross-origin, which is why the backend configures
    // explicit CORS origins with credentials rather than a wildcard.
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/v3/api-docs': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
