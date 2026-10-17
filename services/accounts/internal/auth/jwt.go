// Package auth validates Keycloak-issued JWTs and enforces realm role
// requirements on the Accounts REST API.
package auth

import (
	"context"
	"crypto/rsa"
	"errors"
	"fmt"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
)

// ErrInvalidClaims indicates a token parsed successfully but its claims were
// malformed or the token was otherwise not valid.
var ErrInvalidClaims = errors.New("invalid token claims")

// Claims is the subset of a validated JWT's claims this service cares about.
type Claims struct {
	Subject string
	Roles   []string
}

// Verifier validates JWTs against a trusted public key source.
type Verifier struct {
	keyfunc jwt.Keyfunc
}

// NewVerifier builds a Verifier backed by a live JWKS endpoint (production use).
func NewVerifier(jwksURL string) (*Verifier, error) {
	kf, err := keyfunc.NewDefaultCtx(context.Background(), []string{jwksURL})
	if err != nil {
		return nil, fmt.Errorf("fetch JWKS from %s: %w", jwksURL, err)
	}
	return &Verifier{keyfunc: kf.Keyfunc}, nil
}

// NewVerifierFromKey builds a Verifier backed by a single static public key (test use).
func NewVerifierFromKey(key *rsa.PublicKey) *Verifier {
	return &Verifier{keyfunc: func(*jwt.Token) (interface{}, error) { return key, nil }}
}

// Validate parses and verifies tokenString, returning its claims or an error
// if the signature, expiry, or structure is invalid.
func (v *Verifier) Validate(tokenString string) (*Claims, error) {
	token, err := jwt.Parse(tokenString, v.keyfunc, jwt.WithValidMethods([]string{"RS256"}))
	if err != nil {
		return nil, fmt.Errorf("parse token: %w", err)
	}
	mapClaims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, ErrInvalidClaims
	}
	subject, _ := mapClaims["sub"].(string)
	return &Claims{Subject: subject, Roles: realmRoles(mapClaims)}, nil
}

// realmRoles extracts the realm_access.roles claim Keycloak embeds in every
// access token, already expanded to include composite roles.
func realmRoles(claims jwt.MapClaims) []string {
	realmAccess, ok := claims["realm_access"].(map[string]interface{})
	if !ok {
		return nil
	}
	rawRoles, ok := realmAccess["roles"].([]interface{})
	if !ok {
		return nil
	}
	roles := make([]string, 0, len(rawRoles))
	for _, r := range rawRoles {
		if role, ok := r.(string); ok {
			roles = append(roles, role)
		}
	}
	return roles
}
