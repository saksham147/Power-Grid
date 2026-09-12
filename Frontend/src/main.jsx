import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import Simulation from './routes/Simulation.jsx'
import Plants from './routes/Plants.jsx'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A dead backend should surface immediately as a banner, not after three
      // silent retries — and the data here is cheap to refetch.
      retry: false,
      refetchOnWindowFocus: false,
      staleTime: 1000,
    },
  },
})

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <Simulation /> },
      { path: 'plants', element: <Plants /> },
      { path: '*', element: <Simulation /> },
    ],
  },
])

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
