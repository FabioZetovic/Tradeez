/// <reference types="vitest/config" />
import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

/**
 * A Content-Security-Policy on the built page (Fabio's security pass, session 017). The session token is
 * already out of JavaScript's reach (HttpOnly cookies, DEC-032); this is the next line: scripts only from
 * the app's own origin, so an injected <script> or inline handler would not run even if one got in.
 * Requests go to the app or the API, and nothing may frame or submit elsewhere.
 *
 * Build only: the dev server injects inline scripts for hot reload. As a <meta> it cannot carry
 * frame-ancestors; the host adds that as a header when there is one (DEC-002).
 */
function contentSecurityPolicy(apiBase: string): Plugin {
  const api = apiBase ? ` ${new URL(apiBase).origin}` : ''
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data: blob:",
    `connect-src 'self'${api}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ')
  return {
    name: 'tradeez-csp',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: policy }, injectTo: 'head-prepend' },
    ],
  }
}

export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss(), contentSecurityPolicy(loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL ?? '')],
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
}))
