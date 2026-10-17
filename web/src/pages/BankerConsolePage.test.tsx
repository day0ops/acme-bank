import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { BankerConsolePage } from './BankerConsolePage'

function renderPage() {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <BankerConsolePage />
    </QueryClientProvider>,
  )
}

describe('BankerConsolePage', () => {
  it('looks up a customer by id and displays their accounts', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/customer id/i), 'cust-1')
    await user.click(screen.getByRole('button', { name: /look up/i }))

    await waitFor(() => expect(screen.getByText(/Ada/)).toBeInTheDocument())
  })

  it('submits a transfer and shows a success message', async () => {
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/from account/i), 'acct-1')
    await user.type(screen.getByLabelText(/to account/i), 'acct-2')
    await user.type(screen.getByLabelText(/amount/i), '50.00')
    await user.click(screen.getByRole('button', { name: /send transfer/i }))

    await waitFor(() => expect(screen.getByText(/transfer completed/i)).toBeInTheDocument())
  })
})
