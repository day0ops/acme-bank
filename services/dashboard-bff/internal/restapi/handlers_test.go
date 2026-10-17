package restapi_test

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
	"example.com/acmebank/services/dashboard-bff/internal/aggregator"
	"example.com/acmebank/services/dashboard-bff/internal/auth"
	"example.com/acmebank/services/dashboard-bff/internal/clients"
	"example.com/acmebank/services/dashboard-bff/internal/restapi"
	"github.com/golang-jwt/jwt/v5"
)

func generateTestToken(t *testing.T, key *rsa.PrivateKey, roles []string) string {
	t.Helper()
	claims := jwt.MapClaims{
		"sub": "test-user-id",
		"exp": time.Now().Add(time.Hour).Unix(),
		"realm_access": map[string]interface{}{
			"roles": roles,
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}
	return signed
}

func TestHandlers_GetDashboard_WithValidToken_ReturnsCamelCaseJSON(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := auth.NewVerifierFromKey(&key.PublicKey)
	tokenString := generateTestToken(t, key, []string{"customer"})

	service := aggregator.NewService(
		&clients.FakeCustomersClient{Customer: &customersv1.Customer{Id: "cust-1", FirstName: "Ada"}},
		&clients.FakeAccountsClient{Accounts: []*accountsv1.Account{{
			Id:      "acct-1",
			Type:    accountsv1.AccountType_ACCOUNT_TYPE_CHECKING,
			Status:  accountsv1.AccountStatus_ACCOUNT_STATUS_ACTIVE,
			Balance: &commonv1.Money{CurrencyCode: "USD", MinorUnits: 12345},
		}}},
		&clients.FakeCardsClient{CardsByAccount: map[string][]*cardsv1.Card{
			"acct-1": {{Id: "card-1", LastFour: "4242", Status: cardsv1.CardStatus_CARD_STATUS_ACTIVE}},
		}},
		&clients.FakeTransactionsClient{TransactionsByAccount: map[string][]*transactionsv1.Transaction{
			"acct-1": {{Id: "tx-1", FromAccountId: "acct-1", ToAccountId: "acct-2", Status: transactionsv1.TransactionStatus_TRANSACTION_STATUS_COMPLETED}},
		}},
	)
	mux := restapi.NewMux(service, verifier)

	req := httptest.NewRequest(http.MethodGet, "/dashboard/cust-1", nil)
	req.Header.Set("Authorization", "Bearer "+tokenString)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("got status %d, body %s", rec.Code, rec.Body.String())
	}
	// Decode into map[string]any (rather than the DTO structs) so the
	// assertions below inspect the actual wire-format JSON keys, not just
	// the `json:"..."` tags in the source.
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal response: %v", err)
	}

	customer, ok := body["customer"].(map[string]any)
	if !ok {
		t.Fatalf("expected a customer object, got %v", body["customer"])
	}
	if customer["firstName"] != "Ada" {
		t.Fatalf("got firstName %v, want Ada (camelCase key)", customer["firstName"])
	}

	accounts, ok := body["accounts"].([]any)
	if !ok || len(accounts) != 1 {
		t.Fatalf("expected one account, got %v", body["accounts"])
	}
	accountView, ok := accounts[0].(map[string]any)
	if !ok {
		t.Fatalf("expected an account view object, got %v", accounts[0])
	}
	account, ok := accountView["account"].(map[string]any)
	if !ok {
		t.Fatalf("expected an account object, got %v", accountView["account"])
	}
	if account["type"] != "ACCOUNT_TYPE_CHECKING" {
		t.Fatalf("got type %v, want ACCOUNT_TYPE_CHECKING (string-valued enum)", account["type"])
	}
	if account["status"] != "ACCOUNT_STATUS_ACTIVE" {
		t.Fatalf("got status %v, want ACCOUNT_STATUS_ACTIVE (string-valued enum)", account["status"])
	}
	balance, ok := account["balance"].(map[string]any)
	if !ok {
		t.Fatalf("expected a balance object, got %v", account["balance"])
	}
	if balance["currencyCode"] != "USD" {
		t.Fatalf("got currencyCode %v, want USD (camelCase key)", balance["currencyCode"])
	}

	cards, ok := accountView["cards"].([]any)
	if !ok || len(cards) != 1 {
		t.Fatalf("expected one card, got %v", accountView["cards"])
	}
	card, ok := cards[0].(map[string]any)
	if !ok {
		t.Fatalf("expected a card object, got %v", cards[0])
	}
	if card["lastFour"] != "4242" {
		t.Fatalf("got lastFour %v, want 4242 (camelCase key)", card["lastFour"])
	}

	transactions, ok := accountView["recentTransactions"].([]any)
	if !ok || len(transactions) != 1 {
		t.Fatalf("expected one transaction, got %v", accountView["recentTransactions"])
	}
	transaction, ok := transactions[0].(map[string]any)
	if !ok {
		t.Fatalf("expected a transaction object, got %v", transactions[0])
	}
	if transaction["fromAccountId"] != "acct-1" || transaction["toAccountId"] != "acct-2" {
		t.Fatalf("got fromAccountId %v, toAccountId %v, want acct-1/acct-2 (camelCase keys)", transaction["fromAccountId"], transaction["toAccountId"])
	}
}

// spyCustomersClient records the id it was called with, so tests can assert
// the router extracted the correct path segment rather than relying on a
// fake that would return the same response regardless of the id passed in.
type spyCustomersClient struct{ gotID string }

func (s *spyCustomersClient) GetCustomer(ctx context.Context, id string) (*customersv1.Customer, error) {
	s.gotID = id
	return &customersv1.Customer{Id: id}, nil
}

func TestHandlers_GetDashboard_ExtractsCustomerIDFromPath(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := auth.NewVerifierFromKey(&key.PublicKey)
	tokenString := generateTestToken(t, key, []string{"customer"})

	spy := &spyCustomersClient{}
	service := aggregator.NewService(spy, &clients.FakeAccountsClient{}, &clients.FakeCardsClient{}, &clients.FakeTransactionsClient{})
	mux := restapi.NewMux(service, verifier)

	req := httptest.NewRequest(http.MethodGet, "/dashboard/cust-distinct-42", nil)
	req.Header.Set("Authorization", "Bearer "+tokenString)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("got status %d, body %s", rec.Code, rec.Body.String())
	}
	if spy.gotID != "cust-distinct-42" {
		t.Fatalf("got customer id %q passed to service, want cust-distinct-42 (path value must survive the auth.RequireRole wrapper)", spy.gotID)
	}
}

func TestHandlers_GetDashboard_WithoutToken_Returns401(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := auth.NewVerifierFromKey(&key.PublicKey)
	service := aggregator.NewService(&clients.FakeCustomersClient{}, &clients.FakeAccountsClient{}, &clients.FakeCardsClient{}, &clients.FakeTransactionsClient{})
	mux := restapi.NewMux(service, verifier)

	req := httptest.NewRequest(http.MethodGet, "/dashboard/cust-1", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("got status %d, want 401", rec.Code)
	}
}
