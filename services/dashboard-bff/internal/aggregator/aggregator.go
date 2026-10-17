// Package aggregator assembles the composite dashboard view by fanning out
// to the Customers, Accounts, Cards, and Transactions services.
package aggregator

import (
	"context"
	"fmt"
	"sync"

	"example.com/acmebank/services/dashboard-bff/internal/clients"
	"example.com/acmebank/services/dashboard-bff/internal/domain"
)

// Service assembles the composite dashboard view by fanning out to the
// Customers, Accounts, Cards, and Transactions services.
type Service struct {
	customers    clients.CustomersClient
	accounts     clients.AccountsClient
	cards        clients.CardsClient
	transactions clients.TransactionsClient
}

// NewService builds a Service from its downstream client dependencies.
func NewService(customers clients.CustomersClient, accounts clients.AccountsClient, cards clients.CardsClient, transactions clients.TransactionsClient) *Service {
	return &Service{customers: customers, accounts: accounts, cards: cards, transactions: transactions}
}

// GetDashboard fetches the customer's profile and accounts sequentially (the
// account list is needed before anything else can run), then fans out to
// Cards and Transactions concurrently, once per account.
func (s *Service) GetDashboard(ctx context.Context, customerID string) (*domain.DashboardView, error) {
	customer, err := s.customers.GetCustomer(ctx, customerID)
	if err != nil {
		return nil, fmt.Errorf("get customer: %w", err)
	}

	accounts, err := s.accounts.ListAccountsByCustomer(ctx, customerID)
	if err != nil {
		return nil, fmt.Errorf("list accounts: %w", err)
	}

	accountViews := make([]domain.AccountView, len(accounts))
	errs := make([]error, len(accounts))
	var wg sync.WaitGroup
	for i, account := range accounts {
		wg.Add(1)
		go func(i int, accountID string) {
			defer wg.Done()
			cards, err := s.cards.ListCardsByAccount(ctx, accountID)
			if err != nil {
				errs[i] = fmt.Errorf("list cards for account %s: %w", accountID, err)
				return
			}
			transactions, err := s.transactions.ListTransactionsByAccount(ctx, accountID)
			if err != nil {
				errs[i] = fmt.Errorf("list transactions for account %s: %w", accountID, err)
				return
			}
			accountViews[i] = domain.AccountView{Account: accounts[i], Cards: cards, RecentTransactions: transactions}
		}(i, account.Id)
	}
	wg.Wait()

	for _, err := range errs {
		if err != nil {
			return nil, err
		}
	}

	return &domain.DashboardView{Customer: customer, Accounts: accountViews}, nil
}
