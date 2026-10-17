import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useCustomerSearch } from '@/hooks/useCustomerSearch'
import { useTransfer } from '@/hooks/useTransfer'
import { formatMoney, transactionStatusLabel } from '@/lib/utils'

/**
 * Banker-facing console: look up any customer's dashboard by customer ID,
 * and submit a transfer between two accounts.
 *
 * There is no search-by-name endpoint on any backend service yet, so ID
 * lookup is the only option - a real limitation to flag to product, not
 * something to paper over with a fake endpoint.
 */
export function BankerConsolePage() {
  const [customerIdInput, setCustomerIdInput] = useState('')
  const [searchedCustomerId, setSearchedCustomerId] = useState('')
  const customerSearch = useCustomerSearch(searchedCustomerId)

  function handleLookup(event: FormEvent) {
    event.preventDefault()
    setSearchedCustomerId(customerIdInput.trim())
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold">Banker console</h1>

      <Card>
        <CardHeader>
          <CardTitle>Look up customer</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <form className="flex items-end gap-3" onSubmit={handleLookup}>
            <div className="flex-1">
              <label htmlFor="customer-id" className="text-sm font-medium text-slate-700">
                Customer ID
              </label>
              <Input id="customer-id" value={customerIdInput} onChange={(e) => setCustomerIdInput(e.target.value)} />
            </div>
            <Button type="submit">Look up</Button>
          </form>

          {searchedCustomerId && (
            <div>
              {customerSearch.isLoading && <p>Looking up customer...</p>}
              {customerSearch.isError && <p>No customer found for ID &quot;{searchedCustomerId}&quot;.</p>}
              {customerSearch.data && (
                <div className="space-y-4">
                  <h2 className="text-lg font-semibold">
                    {customerSearch.data.customer.firstName} {customerSearch.data.customer.lastName}
                  </h2>
                  <div className="grid gap-4 md:grid-cols-2">
                    {customerSearch.data.accounts.map((av) => (
                      <Card key={av.account.id}>
                        <CardHeader>
                          <CardTitle>{av.account.type === 'ACCOUNT_TYPE_CHECKING' ? 'Checking' : 'Savings'}</CardTitle>
                        </CardHeader>
                        <CardContent>
                          <p className="text-2xl font-bold">
                            {formatMoney(av.account.balance.minorUnits, av.account.balance.currencyCode)}
                          </p>
                          {av.recentTransactions.length > 0 && (
                            <ul className="mt-4 space-y-1 text-sm text-slate-700">
                              {av.recentTransactions.map((tx) => (
                                <li key={tx.id}>
                                  {tx.fromAccountId === av.account.id ? 'To' : 'From'}{' '}
                                  {tx.fromAccountId === av.account.id ? tx.toAccountId : tx.fromAccountId} ·{' '}
                                  {transactionStatusLabel(tx.status)}
                                </li>
                              ))}
                            </ul>
                          )}
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <TransferForm />
    </div>
  )
}

/** Standalone transfer form: debits `fromAccountId`, credits `toAccountId`. */
function TransferForm() {
  const [fromAccountId, setFromAccountId] = useState('')
  const [toAccountId, setToAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const transfer = useTransfer()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    transfer.mutate({
      fromAccountId,
      toAccountId,
      currencyCode: 'USD',
      amountMinorUnits: Math.round(Number(amount) * 100),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Send transfer</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form className="grid gap-4 md:grid-cols-4 md:items-end" onSubmit={handleSubmit}>
          <div>
            <label htmlFor="from-account" className="text-sm font-medium text-slate-700">
              From account
            </label>
            <Input id="from-account" value={fromAccountId} onChange={(e) => setFromAccountId(e.target.value)} />
          </div>
          <div>
            <label htmlFor="to-account" className="text-sm font-medium text-slate-700">
              To account
            </label>
            <Input id="to-account" value={toAccountId} onChange={(e) => setToAccountId(e.target.value)} />
          </div>
          <div>
            <label htmlFor="amount" className="text-sm font-medium text-slate-700">
              Amount (USD)
            </label>
            <Input id="amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </div>
          <Button type="submit" disabled={transfer.isPending}>
            Send transfer
          </Button>
        </form>

        {transfer.isSuccess && transfer.data.status === 'COMPLETED' && (
          <p className="text-sm text-emerald-600">Transfer completed.</p>
        )}
        {transfer.isSuccess && transfer.data.status !== 'COMPLETED' && (
          <p className="text-sm text-red-600">Transfer failed: {transfer.data.failureReason}</p>
        )}
        {transfer.isError && <p className="text-sm text-red-600">{(transfer.error as Error).message}</p>}
      </CardContent>
    </Card>
  )
}
