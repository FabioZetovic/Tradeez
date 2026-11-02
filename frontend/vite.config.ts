/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

// BUG-166: no Content-Security-Policy here. The server that serves the built page sends one (SecurityConfig, DEC-084);
// a second, older one in a <meta> was enforced as well and refused what the first allowed (data: fonts).
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
    // npm run coverage: which lines the tests run, by file. A map of what is untested, not a target.
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/**/*.d.ts'],
      reporter: ['text-summary', 'html'],
    },
  },
})
