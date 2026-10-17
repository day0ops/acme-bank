import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useDashboard } from '@/hooks/useDashboard'
import { formatMoney, transactionStatusLabel } from '@/lib/utils'

/**
 * Authenticated customer's real dashboard: same visual shape as
 * `SandboxDashboardPage`, but driven by a live `GET /api/dashboard/:customerId`
 * call instead of seeded data.
 */
export function DashboardPage({ customerId }: { customerId: string }) {
  const { data, isLoading, isError } = useDashboard(customerId)

  if (isLoading) {
    return <p>Loading your dashboard...</p>
  }
  if (isError || !data) {
    return <p>We couldn't load your dashboard right now. Please try again shortly.</p>
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Welcome back, {data.customer.firstName}</h1>
      <div className="grid gap-6 md:grid-cols-2">
        {data.accounts.map((av) => (
          <Card key={av.account.id}>
            <CardHeader>
              <CardTitle>{av.account.type === 'ACCOUNT_TYPE_CHECKING' ? 'Checking' : 'Savings'}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-bold">{formatMoney(av.account.balance.minorUnits, av.account.balance.currencyCode)}</p>
              <p className="mt-2 text-sm text-slate-500">
                {av.cards.length} card{av.cards.length === 1 ? '' : 's'} · {av.recentTransactions.length} recent transaction{av.recentTransactions.length === 1 ? '' : 's'}
              </p>
              {av.cards.length > 0 && (
                <ul className="mt-4 space-y-1 text-sm text-slate-700">
                  {av.cards.map((card) => (
                    <li key={card.id}>Card ending {card.lastFour} · {card.status === 'CARD_STATUS_ACTIVE' ? 'Active' : 'Inactive'}</li>
                  ))}
                </ul>
              )}
              {av.recentTransactions.length > 0 && (
                <ul className="mt-4 space-y-1 text-sm text-slate-700">
                  {av.recentTransactions.map((tx) => (
                    <li key={tx.id}>
                      {tx.fromAccountId === av.account.id ? 'To' : 'From'} {tx.fromAccountId === av.account.id ? tx.toAccountId : tx.fromAccountId} · {transactionStatusLabel(tx.status)}
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
