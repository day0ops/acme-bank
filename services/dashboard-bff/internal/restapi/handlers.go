package restapi

import (
	"encoding/json"
	"net/http"

	"example.com/acmebank/services/dashboard-bff/internal/aggregator"
	"example.com/acmebank/services/dashboard-bff/internal/auth"
)

// anyAuthenticatedRole is the set of realm roles permitted to read the
// aggregated dashboard view. Ownership enforcement (a customer only reading
// their own dashboard) is deferred as a follow-up.
var anyAuthenticatedRole = []string{"customer", "banker", "admin"}

// NewMux wires the Dashboard BFF's REST API onto service. The route requires
// a valid Bearer JWT, validated by verifier, held by any authenticated role.
func NewMux(service *aggregator.Service, verifier *auth.Verifier) *http.ServeMux {
	mux := http.NewServeMux()

	mux.Handle("GET /dashboard/{customerId}", auth.RequireRole(verifier, anyAuthenticatedRole...)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		customerID := r.PathValue("customerId")
		view, err := service.GetDashboard(r.Context(), customerID)
		if err != nil {
			writeProblem(w, http.StatusBadGateway, "Bad Gateway", "failed to assemble dashboard: "+err.Error())
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(toDashboardResponse(view))
	})))

	return mux
}
