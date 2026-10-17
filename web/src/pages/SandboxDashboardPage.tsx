import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { sandboxDashboard } from '@/lib/sandboxData'
import { formatMoney, transactionStatusLabel } from '@/lib/utils'

/**
 * Read-only guest dashboard rendered entirely from `sandboxDashboard` (no
 * fetch, no auth, no router params) so it can be shown instantly in a
 * workshop with no backend reachable.
 */
export function SandboxDashboardPage() {
  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
        You're viewing a read-only demo with sample data. <a href="/login" className="font-semibold underline">Log in</a> to see your real accounts.
      </div>
      <h1 className="text-2xl font-bold">Welcome, {sandboxDashboard.customer.firstName}</h1>
      <div className="grid gap-6 md:grid-cols-2">
        {sandboxDashboard.accounts.map((av) => (
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
