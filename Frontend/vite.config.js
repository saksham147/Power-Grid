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
    },
  },
})
