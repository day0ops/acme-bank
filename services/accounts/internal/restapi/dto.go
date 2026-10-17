package restapi

import (
	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
)

// accountResponse is the REST representation of an Account. It mirrors the
// request JSON contract (camelCase keys, string enum values) so a client can
// round-trip a response back into a request body.
type accountResponse struct {
	ID         string        `json:"id"`
	CustomerID string        `json:"customerId"`
	Type       string        `json:"type"`
	Status     string        `json:"status"`
	Balance    moneyResponse `json:"balance"`
}

// moneyResponse is the REST representation of Money.
type moneyResponse struct {
	CurrencyCode string `json:"currencyCode"`
	MinorUnits   int64  `json:"minorUnits"`
}

// toAccountResponse converts a proto Account into its REST representation.
func toAccountResponse(a *accountsv1.Account) accountResponse {
	return accountResponse{
		ID:         a.Id,
		CustomerID: a.CustomerId,
		Type:       a.Type.String(),
		Status:     a.Status.String(),
		Balance: moneyResponse{
			CurrencyCode: a.Balance.CurrencyCode,
			MinorUnits:   a.Balance.MinorUnits,
		},
	}
}
