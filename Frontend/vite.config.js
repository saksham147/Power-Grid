import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // The Producer service sets no CORS headers, so a browser calling :8090
      // from :5173 would be blocked. Fronting it here keeps the app same-origin
      // and leaves the backend untouched.
      '/api': 'http://localhost:8090',
      // Customer listens on its own port with its own /api/*, so it gets a distinct
      // prefix here rather than colliding with Producer's; the rewrite strips the
      // prefix back to the plain /api/* path Customer actually serves.
      '/customer-api': {
        target: 'http://localhost:8091',
        rewrite: (path) => path.replace(/^\/customer-api/, '/api'),
      },
      // Grid: same reasoning as Customer above.
      '/grid-api': {
        target: 'http://localhost:8092',
        rewrite: (path) => path.replace(/^\/grid-api/, '/api'),
      },
      // Distributor: same reasoning as Customer above.
      '/distributor-api': {
        target: 'http://localhost:8093',
        rewrite: (path) => path.replace(/^\/distributor-api/, '/api'),
      },
    },
  },
})
