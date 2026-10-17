package grpcapi_test

import (
	"context"
	"testing"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	"example.com/acmebank/services/accounts/internal/grpcapi"
	"example.com/acmebank/services/accounts/internal/postgres"
)

func TestServer_CreateAndGetAccount(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	ctx := context.Background()

	createResp, err := srv.CreateAccount(ctx, &accountsv1.CreateAccountRequest{
		CustomerId:   "22222222-2222-2222-2222-222222222222",
		Type:         accountsv1.AccountType_ACCOUNT_TYPE_CHECKING,
		CurrencyCode: "USD",
	})
	if err != nil {
		t.Fatalf("CreateAccount() error = %v", err)
	}
	if createResp.Account.Id == "" {
		t.Fatal("expected a generated account ID")
	}

	getResp, err := srv.GetAccount(ctx, &accountsv1.GetAccountRequest{Id: createResp.Account.Id})
	if err != nil {
		t.Fatalf("GetAccount() error = %v", err)
	}
	if getResp.Account.Balance.CurrencyCode != "USD" || getResp.Account.Balance.MinorUnits != 0 {
		t.Fatalf("got balance %+v, want 0 USD", getResp.Account.Balance)
	}
}

func TestServer_Debit_InsufficientFunds(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	ctx := context.Background()

	createResp, err := srv.CreateAccount(ctx, &accountsv1.CreateAccountRequest{
		CustomerId:   "33333333-3333-3333-3333-333333333333",
		Type:         accountsv1.AccountType_ACCOUNT_TYPE_CHECKING,
		CurrencyCode: "USD",
	})
	if err != nil {
		t.Fatalf("CreateAccount() error = %v", err)
	}

	_, err = srv.Debit(ctx, &accountsv1.DebitRequest{
		AccountId:      createResp.Account.Id,
		Amount:         &commonv1.Money{CurrencyCode: "USD", MinorUnits: 500},
		IdempotencyKey: "debit-1",
	})
	if err == nil {
		t.Fatal("expected error for insufficient funds")
	}
}
