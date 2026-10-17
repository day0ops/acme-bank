package auth

import (
	"crypto/rand"
	"crypto/rsa"
	"testing"
	"time"

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

func TestVerifier_Validate_ValidToken(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	verifier := NewVerifierFromKey(&key.PublicKey)

	tokenString := generateTestToken(t, key, []string{"customer"})

	claims, err := verifier.Validate(tokenString)
	if err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	if claims.Subject != "test-user-id" {
		t.Fatalf("got subject %q, want test-user-id", claims.Subject)
	}
	if len(claims.Roles) != 1 || claims.Roles[0] != "customer" {
		t.Fatalf("got roles %v, want [customer]", claims.Roles)
	}
}

func TestVerifier_Validate_WrongKey(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	otherKey, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := NewVerifierFromKey(&otherKey.PublicKey)

	tokenString := generateTestToken(t, key, []string{"customer"})

	_, err := verifier.Validate(tokenString)
	if err == nil {
		t.Fatal("expected error for token signed with a different key")
	}
}

func TestVerifier_Validate_ExpiredToken(t *testing.T) {
	key, _ := rsa.GenerateKey(rand.Reader, 2048)
	verifier := NewVerifierFromKey(&key.PublicKey)

	claims := jwt.MapClaims{
		"sub":          "test-user-id",
		"exp":          time.Now().Add(-time.Hour).Unix(),
		"realm_access": map[string]interface{}{"roles": []string{"customer"}},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, _ := token.SignedString(key)

	_, err := verifier.Validate(signed)
	if err == nil {
		t.Fatal("expected error for expired token")
	}
}
