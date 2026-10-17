package aggregator_test

import (
	"context"
	"errors"
	"testing"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
	"example.com/acmebank/services/dashboard-bff/internal/aggregator"
	"example.com/acmebank/services/dashboard-bff/internal/clients"
)

func TestGetDashboard_AssemblesCustomerWithAccountsCardsAndTransactions(t *testing.T) {
	customersClient := &clients.FakeCustomersClient{Customer: &customersv1.Customer{Id: "cust-1", Email: "a@example.com"}}
	accountsClient := &clients.FakeAccountsClient{Accounts: []*accountsv1.Account{
		{Id: "acct-1", CustomerId: "cust-1"},
		{Id: "acct-2", CustomerId: "cust-1"},
	}}
	cardsClient := &clients.FakeCardsClient{CardsByAccount: map[string][]*cardsv1.Card{
		"acct-1": {{Id: "card-1", AccountId: "acct-1"}},
		"acct-2": {},
	}}
	transactionsClient := &clients.FakeTransactionsClient{TransactionsByAccount: map[string][]*transactionsv1.Transaction{
		"acct-1": {{Id: "tx-1", FromAccountId: "acct-1"}},
		"acct-2": {{Id: "tx-2", ToAccountId: "acct-2"}},
	}}

	service := aggregator.NewService(customersClient, accountsClient, cardsClient, transactionsClient)

	view, err := service.GetDashboard(context.Background(), "cust-1")

	if err != nil {
		t.Fatalf("GetDashboard() error = %v", err)
	}
	if view.Customer.Id != "cust-1" {
		t.Fatalf("got customer id %q, want cust-1", view.Customer.Id)
	}
	if len(view.Accounts) != 2 {
		t.Fatalf("got %d accounts, want 2", len(view.Accounts))
	}
	for _, av := range view.Accounts {
		if av.Account.Id == "acct-1" {
			if len(av.Cards) != 1 || len(av.RecentTransactions) != 1 {
				t.Fatalf("acct-1: got %d cards, %d transactions, want 1 and 1", len(av.Cards), len(av.RecentTransactions))
			}
		}
		if av.Account.Id == "acct-2" {
			if len(av.Cards) != 0 || len(av.RecentTransactions) != 1 {
				t.Fatalf("acct-2: got %d cards, %d transactions, want 0 and 1", len(av.Cards), len(av.RecentTransactions))
			}
		}
	}
}

func TestGetDashboard_CustomerNotFound_PropagatesError(t *testing.T) {
	customersClient := &clients.FakeCustomersClient{Err: errors.New("customer not found")}
	accountsClient := &clients.FakeAccountsClient{}
	cardsClient := &clients.FakeCardsClient{}
	transactionsClient := &clients.FakeTransactionsClient{}

	service := aggregator.NewService(customersClient, accountsClient, cardsClient, transactionsClient)

	_, err := service.GetDashboard(context.Background(), "unknown")

	if err == nil {
		t.Fatal("expected an error when the customer lookup fails")
	}
}

func TestGetDashboard_WithNoAccounts_ReturnsEmptyAccountsList(t *testing.T) {
	customersClient := &clients.FakeCustomersClient{Customer: &customersv1.Customer{Id: "cust-2"}}
	accountsClient := &clients.FakeAccountsClient{Accounts: nil}
	cardsClient := &clients.FakeCardsClient{}
	transactionsClient := &clients.FakeTransactionsClient{}

	service := aggregator.NewService(customersClient, accountsClient, cardsClient, transactionsClient)

	view, err := service.GetDashboard(context.Background(), "cust-2")

	if err != nil {
		t.Fatalf("GetDashboard() error = %v", err)
	}
	if len(view.Accounts) != 0 {
		t.Fatalf("got %d accounts, want 0", len(view.Accounts))
	}
}

func TestGetDashboard_CardsLookupFailsForOneAccount_PropagatesError(t *testing.T) {
	customersClient := &clients.FakeCustomersClient{Customer: &customersv1.Customer{Id: "cust-3"}}
	accountsClient := &clients.FakeAccountsClient{Accounts: []*accountsv1.Account{{Id: "acct-9", CustomerId: "cust-3"}}}
	cardsClient := &clients.FakeCardsClient{Err: errors.New("cards service unavailable")}
	transactionsClient := &clients.FakeTransactionsClient{}

	service := aggregator.NewService(customersClient, accountsClient, cardsClient, transactionsClient)

	_, err := service.GetDashboard(context.Background(), "cust-3")

	if err == nil {
		t.Fatal("expected an error when a downstream fan-out call fails")
	}
}
