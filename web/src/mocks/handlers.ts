import { http, HttpResponse } from 'msw'
import type { DashboardView } from '@/lib/api/types'

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

export const handlers = [
  http.get('/api/dashboard/:customerId', ({ params }) => {
    if (params.customerId !== 'cust-1') {
      return HttpResponse.json({ type: 'about:blank', title: 'Not Found', status: 404 }, { status: 404 })
    }
    return HttpResponse.json(mockDashboard)
  }),
]
