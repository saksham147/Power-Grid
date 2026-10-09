import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const producerHost = process.env.PRODUCER_HOST || 'localhost'
const customerHost = process.env.CUSTOMER_HOST || 'localhost'
const gridHost = process.env.GRID_HOST || 'localhost'
const distributorHost = process.env.DISTRIBUTOR_HOST || 'localhost'
const billingHost = process.env.BILLING_HOST || 'localhost'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,
    port: 5173,
    watch: {
      usePolling: true, // Crucial for Linux / Docker mounts
    },
    proxy: {
      // Use explicit path matching so Vite internal assets like /@react-refresh aren't caught
      '/api': {
        target: `http://${producerHost}:8090`,
        changeOrigin: true,
      },
      '/customer-api': {
        target: `http://${customerHost}:8091`,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/customer-api/, '/api'),
      },
      '/grid-api': {
        target: `http://${gridHost}:8092`,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/grid-api/, '/api'),
      },
      '/distributor-api': {
        target: `http://${distributorHost}:8093`,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/distributor-api/, '/api'),
      },
      '/billing-api': {
        target: `http://${billingHost}:8094`,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/billing-api/, '/api'),
      },
    },
  },
})