import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'

/** Entry point into the Keycloak-hosted login flow. */
export function LoginPage() {
  const { login } = useAuth()

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
