package clients

import (
	"context"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
)

// Compile-time checks that the fakes satisfy their interfaces.
var (
	_ CustomersClient    = (*FakeCustomersClient)(nil)
	_ AccountsClient     = (*FakeAccountsClient)(nil)
	_ CardsClient        = (*FakeCardsClient)(nil)
	_ TransactionsClient = (*FakeTransactionsClient)(nil)
)

// FakeCustomersClient is a test double for CustomersClient returning a
// configured response.
type FakeCustomersClient struct {
	Customer *customersv1.Customer
	Err      error
}

// GetCustomer returns the configured customer or error.
func (f *FakeCustomersClient) GetCustomer(ctx context.Context, id string) (*customersv1.Customer, error) {
	return f.Customer, f.Err
}

// FakeAccountsClient is a test double for AccountsClient.
type FakeAccountsClient struct {
	Accounts []*accountsv1.Account
	Err      error
}

// ListAccountsByCustomer returns the configured accounts or error.
func (f *FakeAccountsClient) ListAccountsByCustomer(ctx context.Context, customerID string) ([]*accountsv1.Account, error) {
	return f.Accounts, f.Err
}

// FakeCardsClient is a test double for CardsClient, keyed by account ID.
type FakeCardsClient struct {
	CardsByAccount map[string][]*cardsv1.Card
	Err            error
}

// ListCardsByAccount returns the configured cards for accountID, or the
// configured error.
func (f *FakeCardsClient) ListCardsByAccount(ctx context.Context, accountID string) ([]*cardsv1.Card, error) {
	if f.Err != nil {
		return nil, f.Err
	}
	return f.CardsByAccount[accountID], nil
}

// FakeTransactionsClient is a test double for TransactionsClient, keyed by
// account ID.
type FakeTransactionsClient struct {
	TransactionsByAccount map[string][]*transactionsv1.Transaction
	Err                   error
}

// ListTransactionsByAccount returns the configured transactions for
// accountID, or the configured error.
func (f *FakeTransactionsClient) ListTransactionsByAccount(ctx context.Context, accountID string) ([]*transactionsv1.Transaction, error) {
	if f.Err != nil {
		return nil, f.Err
	}
	return f.TransactionsByAccount[accountID], nil
}
