package grpcapi_test

import (
	"context"
	"testing"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

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

func TestServer_CreateAccount_InvalidType(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	srv := grpcapi.NewServer(repo)
	ctx := context.Background()
	customerID := "66666666-6666-6666-6666-666666666666"

	_, err := srv.CreateAccount(ctx, &accountsv1.CreateAccountRequest{
		CustomerId:   customerID,
		Type:         accountsv1.AccountType_ACCOUNT_TYPE_UNSPECIFIED,
		CurrencyCode: "USD",
	})
	if err == nil {
		t.Fatal("expected error for unspecified account type")
	}
	if st, ok := status.FromError(err); !ok || st.Code() != codes.InvalidArgument {
		t.Fatalf("got error %v, want codes.InvalidArgument", err)
	}

	accounts, err := repo.ListByCustomer(ctx, customerID)
	if err != nil {
		t.Fatalf("ListByCustomer() error = %v", err)
	}
	if len(accounts) != 0 {
		t.Fatalf("got %d accounts, want 0 (nothing should be persisted)", len(accounts))
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
