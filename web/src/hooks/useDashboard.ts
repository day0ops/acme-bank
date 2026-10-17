import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/lib/api/client'
import type { DashboardView } from '@/lib/api/types'

/** Fetches the authenticated customer's aggregated dashboard from the BFF. */
export function useDashboard(customerId: string) {
  return useQuery({
    queryKey: ['dashboard', customerId],
    queryFn: () => fetchJson<DashboardView>(`/api/dashboard/${customerId}`),
  })
}
