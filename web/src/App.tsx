import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { AppLayout } from '@/components/layout/AppLayout'
import { RequireAuth } from '@/components/auth/RequireAuth'
import { AuthProvider, useAuth } from '@/hooks/useAuth'
import { useNotifications } from '@/hooks/useNotifications'
import { LandingPage } from '@/pages/LandingPage'
import { SandboxDashboardPage } from '@/pages/SandboxDashboardPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { BankerConsolePage } from '@/pages/BankerConsolePage'

const queryClient = new QueryClient()

// TODO(web): there is no customer-lookup-by-Keycloak-subject wiring yet -
// mapping an authenticated Keycloak user to a Customers-service customer ID
// needs either a custom Keycloak user attribute or a lookup-by-email call
// this task doesn't build. Hardcoded/env-var placeholder until that lands.
const DEMO_CUSTOMER_ID = import.meta.env.VITE_DEMO_CUSTOMER_ID ?? 'cust-1'

function Placeholder({ title }: { title: string }) {
  return <h1 className="text-2xl font-bold">{title}</h1>
}

/** Bridges the authenticated token into `useNotifications`; renders nothing. */
function LiveNotifications() {
  const { token } = useAuth()
  useNotifications(token)
  return null
}

/** Application root: providers, router, and route table. */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Toaster richColors position="top-right" />
          <LiveNotifications />
          <Routes>
            <Route element={<AppLayout />}>
              <Route path="/" element={<LandingPage />} />
              <Route path="/sandbox" element={<SandboxDashboardPage />} />
              <Route path="/login" element={<Placeholder title="Login" />} />
              <Route
                path="/dashboard"
                element={
                  <RequireAuth>
                    <DashboardPage customerId={DEMO_CUSTOMER_ID} />
                  </RequireAuth>
                }
              />
              <Route
                path="/banker"
                element={
                  <RequireAuth roles={['banker', 'admin']}>
                    <BankerConsolePage />
                  </RequireAuth>
                }
              />
              <Route path="/admin" element={<Placeholder title="Admin Console" />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
