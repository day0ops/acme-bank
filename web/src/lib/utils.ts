import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { DashboardTransaction } from '@/lib/api/types'

/** Merges conditional class names, resolving conflicting Tailwind utility classes. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Formats minor units (cents) as a localized currency string, e.g. 482319 USD -> "$4,823.19". */
export function formatMoney(minorUnits: number, currencyCode: string) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: currencyCode }).format(minorUnits / 100)
}

const TRANSACTION_STATUS_LABEL: Record<DashboardTransaction['status'], string> = {
  TRANSACTION_STATUS_COMPLETED: 'Completed',
  TRANSACTION_STATUS_PENDING: 'Pending',
  TRANSACTION_STATUS_FAILED: 'Failed',
  TRANSACTION_STATUS_UNSPECIFIED: 'Unknown',
}

/** Human-readable label for a dashboard-embedded transaction's proto-style status constant. */
export function transactionStatusLabel(status: DashboardTransaction['status']) {
  return TRANSACTION_STATUS_LABEL[status]
}
