import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/lib/api/client'
import type { DashboardView } from '@/lib/api/types'

/**
 * Fetches a customer's aggregated dashboard from the BFF. `enabled` lets
 * callers (e.g. the banker console's customer lookup) defer the fetch until
 * a customer ID is actually available; defaults to enabled, as used by the
 * authenticated customer's own dashboard.
 */
export function useDashboard(customerId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['dashboard', customerId],
    queryFn: () => fetchJson<DashboardView>(`/api/dashboard/${customerId}`),
    enabled: options?.enabled,
  })
}
