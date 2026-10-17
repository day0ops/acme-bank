// Package domain holds the Dashboard BFF's composite view types, assembled
// from the Customers, Accounts, Cards, and Transactions services.
package domain

import (
	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
)

// AccountView is one account's full picture: the account itself, its cards,
// and its recent transaction history.
type AccountView struct {
	Account            *accountsv1.Account
	Cards              []*cardsv1.Card
	RecentTransactions []*transactionsv1.Transaction
}

// DashboardView is the composite view the BFF assembles for one customer:
// their profile plus every one of their accounts, each with its own cards
// and transactions.
type DashboardView struct {
	Customer *customersv1.Customer
	Accounts []AccountView
}
