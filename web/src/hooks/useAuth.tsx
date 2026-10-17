import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { getKeycloak } from '@/lib/auth'

interface AuthState {
  isAuthenticated: boolean
  roles: string[]
  token: string | undefined
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

/** Initializes Keycloak (silent SSO check) and provides auth state to the app. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [roles, setRoles] = useState<string[]>([])
  const [token, setToken] = useState<string | undefined>(undefined)
  // Keycloak throws if init() is called twice on the same instance; guard
  // against React StrictMode's dev-only double-invocation of effects.
  const didInit = useRef(false)

  useEffect(() => {
    if (didInit.current) {
      return
    }
    didInit.current = true

    const keycloak = getKeycloak()
    keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256' }).then((authenticated) => {
      setIsAuthenticated(authenticated)
      if (authenticated) {
        setToken(keycloak.token)
        setRoles(keycloak.realmAccess?.roles ?? [])
      }
    })
  }, [])

  const login = () => getKeycloak().login()
  const logout = () => getKeycloak().logout()

  return (
    <AuthContext.Provider value={{ isAuthenticated, roles, token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

/** Reads auth state from the nearest `AuthProvider`. Throws outside one. */
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
