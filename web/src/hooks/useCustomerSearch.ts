import { useDashboard } from './useDashboard'

/**
 * Looks up any customer's dashboard by a banker-provided customer ID, rather
 * than the logged-in user's own ID. Thin wrapper around `useDashboard` - same
 * endpoint and query logic, just gated so it doesn't fetch until a customer
 * ID has actually been searched for.
 */
export function useCustomerSearch(customerId: string) {
  return useDashboard(customerId, { enabled: customerId.length > 0 })
}
