package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRequireRole_ValidTokenWithRole_CallsNext(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := NewVerifierFromKey(&key.PublicKey)
	tokenString := generateTestToken(t, key, []string{"banker"})

	called := false
	handler := RequireRole(verifier, "banker", "admin")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest(http.MethodPost, "/accounts", nil)
	req.Header.Set("Authorization", "Bearer "+tokenString)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if !called {
		t.Fatal("expected next handler to be called")
	}
	if rec.Code != http.StatusOK {
		t.Fatalf("got status %d, want 200", rec.Code)
	}
}

func TestRequireRole_MissingRequiredRole_Returns403(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := NewVerifierFromKey(&key.PublicKey)
	tokenString := generateTestToken(t, key, []string{"customer"})

	handler := RequireRole(verifier, "banker", "admin")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("next handler should not be called")
	}))

	req := httptest.NewRequest(http.MethodPost, "/accounts", nil)
	req.Header.Set("Authorization", "Bearer "+tokenString)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("got status %d, want 403", rec.Code)
	}
	assertProblemDetails(t, rec, http.StatusForbidden)
}

func TestRequireRole_MissingToken_Returns401(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := NewVerifierFromKey(&key.PublicKey)

	handler := RequireRole(verifier, "customer")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("next handler should not be called")
	}))

	req := httptest.NewRequest(http.MethodGet, "/accounts/123", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("got status %d, want 401", rec.Code)
	}
	assertProblemDetails(t, rec, http.StatusUnauthorized)
}

// assertProblemDetails checks that rec carries an RFC 9457 Problem Details
// body matching wantStatus: application/problem+json content type and a
// "status" field equal to the HTTP status code.
func assertProblemDetails(t *testing.T, rec *httptest.ResponseRecorder, wantStatus int) {
	t.Helper()
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Fatalf("got Content-Type %q, want application/problem+json", ct)
	}
	var body problem
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode problem details body: %v", err)
	}
	if body.Status != wantStatus {
		t.Fatalf("got problem status %d, want %d", body.Status, wantStatus)
	}
}
