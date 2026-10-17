import { http, HttpResponse } from 'msw'
import type { DashboardView, Transaction } from '@/lib/api/types'

// Shaped exactly like the dashboard-bff's real response (see sandboxData.ts's
// comment for the full rationale): no customerId/accountId on the embedded
// account/card, no dateOfBirth on the embedded customer, and no
// amount/currency on the embedded transaction.
export const mockDashboard: DashboardView = {
  customer: { id: 'cust-1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' },
  accounts: [
    {
      account: { id: 'acct-1', type: 'ACCOUNT_TYPE_CHECKING', status: 'ACCOUNT_STATUS_ACTIVE', balance: { currencyCode: 'USD', minorUnits: 10000 } },
      cards: [{ id: 'card-1', lastFour: '1234', status: 'CARD_STATUS_ACTIVE' }],
      recentTransactions: [{ id: 'tx-1', fromAccountId: 'acct-1', toAccountId: 'acct-2', status: 'TRANSACTION_STATUS_COMPLETED' }],
    },
  ],
}

// Request body for POST /transactions, per the Transactions service's own
// TransactionController.TransferBody - amountMinorUnits is an integer (cents),
// not a decimal string, and there is no top-level "amount" field.
interface TransferRequestBody {
  fromAccountId: string
  toAccountId: string
  currencyCode: string
  amountMinorUnits: number
}

export const handlers = [
  http.get('/api/dashboard/:customerId', ({ params }) => {
    if (params.customerId !== 'cust-1') {
      return HttpResponse.json({ type: 'about:blank', title: 'Not Found', status: 404 }, { status: 404 })
    }
    return HttpResponse.json(mockDashboard)
  }),

  http.post('/api/transactions', async ({ request }) => {
    const body = (await request.json()) as TransferRequestBody

    // Mirrors Transaction.initiate's own validation: the real Transactions
    // service rejects a same-account transfer with a 400 Problem Details
    // body (IllegalArgumentException), not a FAILED transaction resource.
    if (body.fromAccountId === body.toAccountId) {
      return HttpResponse.json(
        { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'fromAccountId and toAccountId must differ' },
        { status: 400 },
      )
    }

    const transaction: Transaction = {
      id: 'tx-mock-1',
      fromAccountId: body.fromAccountId,
      toAccountId: body.toAccountId,
      currencyCode: body.currencyCode,
      amountMinorUnits: body.amountMinorUnits,
      status: 'COMPLETED',
    }
    return HttpResponse.json(transaction, { status: 201 })
  }),
]
