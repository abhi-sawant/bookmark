import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath } from 'node:url'

const API_ORIGIN = 'https://api.bookmark.slowatcoding.com'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // A new build waits until the user taps "Reload" (see app/pwa.ts) so an open sheet is never lost.
      registerType: 'prompt',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Bookmarks',
        short_name: 'Bookmarks',
        description: 'Your bookmarks, on every device.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#f2f3f4',
        theme_color: '#f2f3f4',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
        shortcuts: [{ name: 'Add bookmark', url: '/add', icons: [{ src: 'icon-192.png', sizes: '192x192' }] }],
        // Android "Share" sheet -> /add?url=&text=&title= (installed Chrome/Edge PWAs).
        share_target: { action: '/add', method: 'GET', params: { title: 'title', text: 'text', url: 'url' } },
      },
      workbox: {
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        globPatterns: ['**/*.{js,css,html,svg,png,ttf,webmanifest}'],
        runtimeCaching: [
          {
            // Uploaded thumbnails: show the cached copy immediately, refresh in the background.
            urlPattern: ({ url }) => url.origin === API_ORIGIN && url.pathname.startsWith('/uploads/thumbnails/'),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'thumbnails',
              expiration: { maxEntries: 600, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  server: { port: Number(process.env.PORT) || 5173 },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
})
