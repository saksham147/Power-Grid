import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Each backend's hostname, not just its port: running locally (`npm run dev` on the host) every
// service is reachable at localhost; running in Docker Compose each one is its own container, so
// `localhost` from inside the frontend container would mean the frontend container itself.
// docker-compose.yml sets these to the service names (producer, customer, ...); left unset, they
// default to localhost so this file still works unchanged for local, non-Docker dev.
const producerHost = process.env.PRODUCER_HOST || 'localhost'
const customerHost = process.env.CUSTOMER_HOST || 'localhost'
const gridHost = process.env.GRID_HOST || 'localhost'
const distributorHost = process.env.DISTRIBUTOR_HOST || 'localhost'
const billingHost = process.env.BILLING_HOST || 'localhost'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 0.0.0.0, not just loopback: inside a container, the host's `docker compose` port mapping
    // can only reach a server bound to every interface -- a plain `vite` (loopback-only) would
    // accept connections from nothing outside the container.
    host: true,
    proxy: {
      // The Producer service sets no CORS headers, so a browser calling :8090
      // from :5173 would be blocked. Fronting it here keeps the app same-origin
      // and leaves the backend untouched.
      '/api': `http://${producerHost}:8090`,
      // Customer listens on its own port with its own /api/*, so it gets a distinct
      // prefix here rather than colliding with Producer's; the rewrite strips the
      // prefix back to the plain /api/* path Customer actually serves.
      '/customer-api': {
        target: `http://${customerHost}:8091`,
        rewrite: (path) => path.replace(/^\/customer-api/, '/api'),
      },
      // Grid: same reasoning as Customer above.
      '/grid-api': {
        target: `http://${gridHost}:8092`,
        rewrite: (path) => path.replace(/^\/grid-api/, '/api'),
      },
      // Distributor: same reasoning as Customer above.
      '/distributor-api': {
        target: `http://${distributorHost}:8093`,
        rewrite: (path) => path.replace(/^\/distributor-api/, '/api'),
      },
      // Billing: same reasoning as Customer above.
      '/billing-api': {
        target: `http://${billingHost}:8094`,
        rewrite: (path) => path.replace(/^\/billing-api/, '/api'),
      },
    },
  },
})
