package clients

import (
	"context"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
)

// Compile-time checks that the gRPC wrappers satisfy their interfaces.
var (
	_ CustomersClient    = (*GrpcCustomersClient)(nil)
	_ AccountsClient     = (*GrpcAccountsClient)(nil)
	_ CardsClient        = (*GrpcCardsClient)(nil)
	_ TransactionsClient = (*GrpcTransactionsClient)(nil)
)

// GrpcCustomersClient implements CustomersClient over the Customers
// service's gRPC stub.
type GrpcCustomersClient struct {
	Stub customersv1.CustomersServiceClient
}

// GetCustomer fetches a customer's profile from the Customers service.
func (c *GrpcCustomersClient) GetCustomer(ctx context.Context, id string) (*customersv1.Customer, error) {
	resp, err := c.Stub.GetCustomer(ctx, &customersv1.GetCustomerRequest{Id: id})
	if err != nil {
		return nil, err
	}
	return resp.Customer, nil
}

// GrpcAccountsClient implements AccountsClient over the Accounts service's
// gRPC stub.
type GrpcAccountsClient struct {
	Stub accountsv1.AccountsServiceClient
}

// ListAccountsByCustomer lists a customer's accounts from the Accounts
// service.
func (c *GrpcAccountsClient) ListAccountsByCustomer(ctx context.Context, customerID string) ([]*accountsv1.Account, error) {
	resp, err := c.Stub.ListAccountsByCustomer(ctx, &accountsv1.ListAccountsByCustomerRequest{CustomerId: customerID})
	if err != nil {
		return nil, err
	}
	return resp.Accounts, nil
}

// GrpcCardsClient implements CardsClient over the Cards service's gRPC
// stub.
type GrpcCardsClient struct {
	Stub cardsv1.CardsServiceClient
}

// ListCardsByAccount lists the cards issued against an account from the
// Cards service.
func (c *GrpcCardsClient) ListCardsByAccount(ctx context.Context, accountID string) ([]*cardsv1.Card, error) {
	resp, err := c.Stub.ListCardsByAccount(ctx, &cardsv1.ListCardsByAccountRequest{AccountId: accountID})
	if err != nil {
		return nil, err
	}
	return resp.Cards, nil
}

// GrpcTransactionsClient implements TransactionsClient over the
// Transactions service's gRPC stub.
type GrpcTransactionsClient struct {
	Stub transactionsv1.TransactionsServiceClient
}

// ListTransactionsByAccount lists the transactions touching an account
// from the Transactions service.
func (c *GrpcTransactionsClient) ListTransactionsByAccount(ctx context.Context, accountID string) ([]*transactionsv1.Transaction, error) {
	resp, err := c.Stub.ListTransactionsByAccount(ctx, &transactionsv1.ListTransactionsByAccountRequest{AccountId: accountID})
	if err != nil {
		return nil, err
	}
	return resp.Transactions, nil
}
