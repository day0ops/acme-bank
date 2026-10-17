import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { DashboardPage } from './DashboardPage'

function renderWithQueryClient(ui: React.ReactElement) {
  // retry: false - the error-state test needs the failed query to settle
  // immediately instead of waiting through TanStack Query's default retries.
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('DashboardPage', () => {
  it("renders the authenticated customer's real account data from the API", async () => {
    renderWithQueryClient(<DashboardPage customerId="cust-1" />)

    await waitFor(() => expect(screen.getByText(/Ada/)).toBeInTheDocument())
    expect(screen.getByText(/\$100\.00/)).toBeInTheDocument()
  })

  it('shows an error state when the dashboard fetch fails', async () => {
    renderWithQueryClient(<DashboardPage customerId="unknown-customer" />)

    await waitFor(() => expect(screen.getByText(/couldn't load/i)).toBeInTheDocument())
  })
})
