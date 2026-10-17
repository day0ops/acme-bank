package restapi

import (
	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
	"example.com/acmebank/services/dashboard-bff/internal/domain"
)

// customerResponse is the REST representation of a Customer.
type customerResponse struct {
	ID        string `json:"id"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	Email     string `json:"email"`
}

func toCustomerResponse(c *customersv1.Customer) customerResponse {
	return customerResponse{ID: c.Id, FirstName: c.FirstName, LastName: c.LastName, Email: c.Email}
}

// moneyResponse is the REST representation of Money.
type moneyResponse struct {
	CurrencyCode string `json:"currencyCode"`
	MinorUnits   int64  `json:"minorUnits"`
}

func toMoneyResponse(m *accountsv1.Account) moneyResponse {
	return moneyResponse{CurrencyCode: m.Balance.CurrencyCode, MinorUnits: m.Balance.MinorUnits}
}

// accountResponse is the REST representation of an Account. Type and Status
// are rendered as their string enum names rather than raw proto numbers.
type accountResponse struct {
	ID      string        `json:"id"`
	Type    string        `json:"type"`
	Status  string        `json:"status"`
	Balance moneyResponse `json:"balance"`
}

func toAccountResponse(a *accountsv1.Account) accountResponse {
	return accountResponse{ID: a.Id, Type: a.Type.String(), Status: a.Status.String(), Balance: toMoneyResponse(a)}
}

// cardResponse is the REST representation of a Card.
type cardResponse struct {
	ID       string `json:"id"`
	LastFour string `json:"lastFour"`
	Status   string `json:"status"`
}

func toCardResponse(c *cardsv1.Card) cardResponse {
	return cardResponse{ID: c.Id, LastFour: c.LastFour, Status: c.Status.String()}
}

// transactionResponse is the REST representation of a Transaction.
type transactionResponse struct {
	ID            string `json:"id"`
	FromAccountID string `json:"fromAccountId"`
	ToAccountID   string `json:"toAccountId"`
	Status        string `json:"status"`
}

func toTransactionResponse(t *transactionsv1.Transaction) transactionResponse {
	return transactionResponse{ID: t.Id, FromAccountID: t.FromAccountId, ToAccountID: t.ToAccountId, Status: t.Status.String()}
}

// accountViewResponse is the REST representation of one account's full
// picture: the account itself, its cards, and its recent transactions.
type accountViewResponse struct {
	Account            accountResponse       `json:"account"`
	Cards              []cardResponse        `json:"cards"`
	RecentTransactions []transactionResponse `json:"recentTransactions"`
}

// dashboardResponse is the REST representation of the composite dashboard
// view: a customer's profile plus every one of their accounts.
type dashboardResponse struct {
	Customer customerResponse      `json:"customer"`
	Accounts []accountViewResponse `json:"accounts"`
}

func toDashboardResponse(view *domain.DashboardView) dashboardResponse {
	accounts := make([]accountViewResponse, 0, len(view.Accounts))
	for _, av := range view.Accounts {
		cards := make([]cardResponse, 0, len(av.Cards))
		for _, c := range av.Cards {
			cards = append(cards, toCardResponse(c))
		}
		transactions := make([]transactionResponse, 0, len(av.RecentTransactions))
		for _, t := range av.RecentTransactions {
			transactions = append(transactions, toTransactionResponse(t))
		}
		accounts = append(accounts, accountViewResponse{
			Account:            toAccountResponse(av.Account),
			Cards:              cards,
			RecentTransactions: transactions,
		})
	}
	return dashboardResponse{Customer: toCustomerResponse(view.Customer), Accounts: accounts}
}
