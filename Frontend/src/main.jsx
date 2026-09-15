import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import Dashboard from './Dashboard.jsx'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A dead backend should surface immediately as an offline node, not after three
      // silent retries — and the data here is cheap to refetch.
      retry: false,
      refetchOnWindowFocus: false,
      staleTime: 1000,
    },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Dashboard />
    </QueryClientProvider>
  </StrictMode>,
)
