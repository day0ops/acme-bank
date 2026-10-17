package restapi

import (
	"encoding/json"
	"net/http"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	"example.com/acmebank/services/accounts/internal/auth"
)

type createAccountBody struct {
	CustomerID   string `json:"customerId"`
	Type         string `json:"type"`
	CurrencyCode string `json:"currencyCode"`
}

// anyAuthenticatedRole is the set of realm roles permitted to read account
// data. Ownership enforcement (a customer only reading their own accounts)
// is deferred as a follow-up.
var anyAuthenticatedRole = []string{"customer", "banker", "admin"}

// NewMux wires the Accounts REST API onto server, the same
// accountsv1.AccountsServiceServer implementation the gRPC transport uses.
// Every route requires a valid Bearer JWT, validated by verifier; opening an
// account additionally requires the banker or admin role.
func NewMux(server accountsv1.AccountsServiceServer, verifier *auth.Verifier) *http.ServeMux {
	mux := http.NewServeMux()

	mux.Handle("POST /accounts", auth.RequireRole(verifier, "banker", "admin")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body createAccountBody
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeProblem(w, http.StatusBadRequest, "Bad Request", "invalid JSON body")
			return
		}
		resp, err := server.CreateAccount(r.Context(), &accountsv1.CreateAccountRequest{
			CustomerId:   body.CustomerID,
			Type:         accountsv1.AccountType(accountsv1.AccountType_value[body.Type]),
			CurrencyCode: body.CurrencyCode,
		})
		if err != nil {
			writeGRPCError(w, err)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_ = json.NewEncoder(w).Encode(toAccountResponse(resp.Account))
	})))

	mux.Handle("GET /accounts/{id}", auth.RequireRole(verifier, anyAuthenticatedRole...)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		resp, err := server.GetAccount(r.Context(), &accountsv1.GetAccountRequest{Id: r.PathValue("id")})
		if err != nil {
			writeGRPCError(w, err)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(toAccountResponse(resp.Account))
	})))

	mux.Handle("GET /accounts", auth.RequireRole(verifier, anyAuthenticatedRole...)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		customerID := r.URL.Query().Get("customerId")
		if customerID == "" {
			writeProblem(w, http.StatusBadRequest, "Bad Request", "customerId query parameter is required")
			return
		}
		resp, err := server.ListAccountsByCustomer(r.Context(), &accountsv1.ListAccountsByCustomerRequest{
			CustomerId: customerID,
		})
		if err != nil {
			writeGRPCError(w, err)
			return
		}
		accounts := []accountResponse{}
		for _, a := range resp.Accounts {
			accounts = append(accounts, toAccountResponse(a))
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(accounts)
	})))

	return mux
}
