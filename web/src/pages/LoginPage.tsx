import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'

/**
 * Entry point into the Keycloak-hosted login flow. Keycloak redirects back
 * here (not to /dashboard) after a successful login, since redirect_uri is
 * wherever the user clicked "Log in" from - so once isAuthenticated flips to
 * true, send them on rather than leaving them stuck looking at this button.
 */
export function LoginPage() {
  const { isAuthenticated, login } = useAuth()

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <div className="mx-auto max-w-sm space-y-4 py-12 text-center">
      <h1 className="text-2xl font-bold">Log in to Acme Bank</h1>
      <p className="text-sm text-slate-500">You'll be redirected to our secure login page.</p>
      <Button size="lg" onClick={login}>
        Log in
      </Button>
    </div>
  )
}
