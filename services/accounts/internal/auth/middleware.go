package auth

import (
	"context"
	"net/http"
	"strings"
)

type contextKey string

const claimsContextKey contextKey = "auth-claims"

// RequireRole returns middleware that rejects requests without a valid Bearer
// JWT (401) or without at least one of the given roles (403), and otherwise
// attaches the validated Claims to the request context before calling next.
//
// Keycloak expands composite roles into realm_access.roles at token issuance
// time, so checking for a literal required role name here is sufficient; no
// special-casing of "admin" is needed.
func RequireRole(verifier *Verifier, roles ...string) func(http.Handler) http.Handler {
	allowed := make(map[string]struct{}, len(roles))
	for _, role := range roles {
		allowed[role] = struct{}{}
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			tokenString, ok := bearerToken(r)
			if !ok {
				writeProblem(w, http.StatusUnauthorized, "Unauthorized", "missing or invalid bearer token")
				return
			}
			claims, err := verifier.Validate(tokenString)
			if err != nil {
				writeProblem(w, http.StatusUnauthorized, "Unauthorized", "missing or invalid bearer token")
				return
			}
			if !hasAnyRole(claims.Roles, allowed) {
				writeProblem(w, http.StatusForbidden, "Forbidden", "caller lacks a required role")
				return
			}
			ctx := context.WithValue(r.Context(), claimsContextKey, claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// bearerToken extracts the token from a request's "Authorization: Bearer
// <token>" header.
func bearerToken(r *http.Request) (string, bool) {
	authHeader := r.Header.Get("Authorization")
	token, ok := strings.CutPrefix(authHeader, "Bearer ")
	if !ok || token == "" {
		return "", false
	}
	return token, true
}

// hasAnyRole reports whether roles contains at least one entry in allowed.
func hasAnyRole(roles []string, allowed map[string]struct{}) bool {
	for _, role := range roles {
		if _, ok := allowed[role]; ok {
			return true
		}
	}
	return false
}

// ClaimsFromContext retrieves the Claims attached by RequireRole, if any.
func ClaimsFromContext(ctx context.Context) (*Claims, bool) {
	claims, ok := ctx.Value(claimsContextKey).(*Claims)
	return claims, ok
}
