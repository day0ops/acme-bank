package restapi_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	"example.com/acmebank/services/accounts/internal/grpcapi"
	"example.com/acmebank/services/accounts/internal/postgres"
	"example.com/acmebank/services/accounts/internal/restapi"
)

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
	var created accountsv1.Account
	if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
		t.Fatalf("unmarshal create response: %v", err)
	}
	if created.Id == "" {
		t.Fatal("expected a generated account ID")
	}

	getReq := httptest.NewRequest(http.MethodGet, "/accounts/"+created.Id, nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)

	if getRec.Code != http.StatusOK {
		t.Fatalf("GET /accounts/{id}: got status %d, body %s", getRec.Code, getRec.Body.String())
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
