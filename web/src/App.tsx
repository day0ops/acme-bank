import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { AppLayout } from '@/components/layout/AppLayout'
import { LandingPage } from '@/pages/LandingPage'
import { SandboxDashboardPage } from '@/pages/SandboxDashboardPage'

const queryClient = new QueryClient()

function Placeholder({ title }: { title: string }) {
  return <h1 className="text-2xl font-bold">{title}</h1>
}

/** Application root: providers, router, and route table. */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Toaster richColors position="top-right" />
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<LandingPage />} />
            <Route path="/sandbox" element={<SandboxDashboardPage />} />
            <Route path="/login" element={<Placeholder title="Login" />} />
            <Route path="/dashboard" element={<Placeholder title="Dashboard" />} />
            <Route path="/banker" element={<Placeholder title="Banker Console" />} />
            <Route path="/admin" element={<Placeholder title="Admin Console" />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
