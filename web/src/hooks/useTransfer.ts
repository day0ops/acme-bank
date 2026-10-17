import { useMutation } from '@tanstack/react-query'
import { fetchJson } from '@/lib/api/client'
import type { Transaction } from '@/lib/api/types'

// Matches the Transactions service's own TransferBody record exactly
// (TransactionController.java): amountMinorUnits is an integer number of the
// currency's minor unit, not a decimal string.
export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  currencyCode: string
  amountMinorUnits: number
}

/** Submits a transfer directly to the Transactions service's `POST /transactions` endpoint. */
export function useTransfer() {
  return useMutation({
    mutationFn: (request: TransferRequest) =>
      fetchJson<Transaction>('/api/transactions', {
        method: 'POST',
        body: JSON.stringify(request),
      }),
  })
}
