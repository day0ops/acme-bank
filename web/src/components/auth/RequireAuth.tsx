import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

interface RequireAuthProps {
  children: ReactNode
  /** Realm roles allowed to view the route; any match grants access. Omit to allow any authenticated user. */
  roles?: string[]
}

/**
 * Redirects to `/login` unless the current user is authenticated, and to `/`
 * if `roles` is given and the user holds none of them.
 */
export function RequireAuth({ children, roles }: RequireAuthProps) {
  const { isAuthenticated, roles: userRoles } = useAuth()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  if (roles && !roles.some((role) => userRoles.includes(role))) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}
