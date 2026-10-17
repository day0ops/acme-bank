// Seeded, static dashboard used by the public sandbox and the landing page
// preview. Shaped exactly like the dashboard-bff's real response (see the
// Dashboard* types in ./api/types): no customerId on the embedded account,
// no accountId on the embedded card, no dateOfBirth on the embedded
// customer, and no amount/currency on the embedded transaction - the BFF's
// DTOs are narrower than the services' own, not a mistake to "complete".
import type { DashboardView } from './api/types'

export const sandboxDashboard: DashboardView = {
  customer: {
    id: 'sandbox-customer',
    firstName: 'Jordan',
    lastName: 'Rivera',
    email: 'jordan.rivera@example.com',
  },
  accounts: [
    {
      account: {
        id: 'sandbox-checking',
        type: 'ACCOUNT_TYPE_CHECKING',
        status: 'ACCOUNT_STATUS_ACTIVE',
        balance: { currencyCode: 'USD', minorUnits: 482319 },
      },
      cards: [{ id: 'sandbox-card-1', lastFour: '4242', status: 'CARD_STATUS_ACTIVE' }],
      recentTransactions: [
        { id: 'sandbox-tx-1', fromAccountId: 'sandbox-checking', toAccountId: 'external', status: 'TRANSACTION_STATUS_COMPLETED' },
        { id: 'sandbox-tx-2', fromAccountId: 'external', toAccountId: 'sandbox-checking', status: 'TRANSACTION_STATUS_COMPLETED' },
      ],
    },
    {
      account: {
        id: 'sandbox-savings',
        type: 'ACCOUNT_TYPE_SAVINGS',
        status: 'ACCOUNT_STATUS_ACTIVE',
        balance: { currencyCode: 'USD', minorUnits: 1540000 },
      },
      cards: [],
      recentTransactions: [],
    },
  ],
}
