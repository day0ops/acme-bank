// Package clients defines the Dashboard BFF's interfaces onto the four
// downstream domain services it fans out to, plus their fake and gRPC
// implementations.
package clients

import (
	"context"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
)

// CustomersClient fetches a customer's profile.
type CustomersClient interface {
	GetCustomer(ctx context.Context, id string) (*customersv1.Customer, error)
}

// AccountsClient lists a customer's accounts.
type AccountsClient interface {
	ListAccountsByCustomer(ctx context.Context, customerID string) ([]*accountsv1.Account, error)
}

// CardsClient lists the cards issued against an account.
type CardsClient interface {
	ListCardsByAccount(ctx context.Context, accountID string) ([]*cardsv1.Card, error)
}

// TransactionsClient lists the transactions touching an account.
type TransactionsClient interface {
	ListTransactionsByAccount(ctx context.Context, accountID string) ([]*transactionsv1.Transaction, error)
}
