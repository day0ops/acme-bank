package restapi_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"example.com/acmebank/services/accounts/internal/grpcapi"
	"example.com/acmebank/services/accounts/internal/postgres"
	"example.com/acmebank/services/accounts/internal/restapi"
)

// accountResponse mirrors restapi's unexported accountResponse shape so
// tests can assert on the REST JSON contract (camelCase, string enums)
// without depending on the proto struct's JSON tags.
type accountResponse struct {
	ID         string `json:"id"`
	CustomerID string `json:"customerId"`
	Type       string `json:"type"`
	Status     string `json:"status"`
	Balance    struct {
		CurrencyCode string `json:"currencyCode"`
		MinorUnits   int64  `json:"minorUnits"`
	} `json:"balance"`
}

func TestHandlers_CreateAndGetAccount(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	mux := restapi.NewMux(srv)

	createJSON := `{"customerId":"44444444-4444-4444-4444-444444444444",` +
		`"type":"ACCOUNT_TYPE_SAVINGS","currencyCode":"USD"}`
	req := httptest.NewRequest(http.MethodPost, "/accounts", strings.NewReader(createJSON))
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("POST /accounts: got status %d, body %s", rec.Code, rec.Body.String())
	}
	var created accountResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
		t.Fatalf("unmarshal create response: %v", err)
	}
	if created.ID == "" {
		t.Fatal("expected a generated account ID")
	}
	if created.CustomerID != "44444444-4444-4444-4444-444444444444" {
		t.Fatalf("got customerId %q, want the requested customer ID", created.CustomerID)
	}
	if created.Type != "ACCOUNT_TYPE_SAVINGS" {
		t.Fatalf("got type %q, want ACCOUNT_TYPE_SAVINGS", created.Type)
	}
	if created.Balance.CurrencyCode != "USD" || created.Balance.MinorUnits != 0 {
		t.Fatalf("got balance %+v, want 0 USD", created.Balance)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/accounts/"+created.ID, nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)

	if getRec.Code != http.StatusOK {
		t.Fatalf("GET /accounts/{id}: got status %d, body %s", getRec.Code, getRec.Body.String())
	}
	var fetched accountResponse
	if err := json.Unmarshal(getRec.Body.Bytes(), &fetched); err != nil {
		t.Fatalf("unmarshal get response: %v", err)
	}
	if fetched.ID != created.ID {
		t.Fatalf("got id %q, want %q", fetched.ID, created.ID)
	}
}

func TestHandlers_CreateAccount_InvalidType_ReturnsProblemDetails(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	mux := restapi.NewMux(srv)

	createJSON := `{"customerId":"55555555-5555-5555-5555-555555555555",` +
		`"type":"NOT_A_REAL_TYPE","currencyCode":"USD"}`
	req := httptest.NewRequest(http.MethodPost, "/accounts", strings.NewReader(createJSON))
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("got status %d, want 400, body %s", rec.Code, rec.Body.String())
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Fatalf("got Content-Type %q, want application/problem+json", ct)
	}
	var problem restapi.Problem
	if err := json.Unmarshal(rec.Body.Bytes(), &problem); err != nil {
		t.Fatalf("unmarshal problem response: %v", err)
	}
	if problem.Status != http.StatusBadRequest {
		t.Fatalf("problem.Status = %d, want 400", problem.Status)
	}
}

func TestHandlers_GetAccount_NotFound_ReturnsProblemDetails(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	mux := restapi.NewMux(srv)

	req := httptest.NewRequest(http.MethodGet, "/accounts/00000000-0000-0000-0000-000000000000", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("got status %d, want 404", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Fatalf("got Content-Type %q, want application/problem+json", ct)
	}
	var problem restapi.Problem
	if err := json.Unmarshal(rec.Body.Bytes(), &problem); err != nil {
		t.Fatalf("unmarshal problem response: %v", err)
	}
	if problem.Status != http.StatusNotFound {
		t.Fatalf("problem.Status = %d, want 404", problem.Status)
	}
}
