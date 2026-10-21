package postgres_test

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	"example.com/acmebank/services/accounts/internal/domain"
	"example.com/acmebank/services/accounts/internal/postgres"
)

func setupTestDB(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()

	req := testcontainers.ContainerRequest{
		Image:        "postgres:16-alpine",
		ExposedPorts: []string{"5432/tcp"},
		Env: map[string]string{
			"POSTGRES_PASSWORD": "test",
			"POSTGRES_DB":       "accounts",
		},
		// The official postgres image logs this line twice: once for the
		// temporary initdb instance, once for the real long-running one. A
		// plain port-open check races the restart between those two and can
		// connect while the first instance is still shutting down.
		WaitingFor: wait.ForLog("database system is ready to accept connections").
			WithOccurrence(2).WithStartupTimeout(60 * time.Second),
	}
	container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		t.Fatalf("failed to start postgres container: %v", err)
	}
	t.Cleanup(func() { _ = container.Terminate(ctx) })

	host, err := container.Host(ctx)
	if err != nil {
		t.Fatalf("failed to get container host: %v", err)
	}
	port, err := container.MappedPort(ctx, "5432")
	if err != nil {
		t.Fatalf("failed to get mapped port: %v", err)
	}

	dsn := "postgres://postgres:test@" + host + ":" + port.Port() + "/accounts?sslmode=disable"
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("failed to connect: %v", err)
	}
	t.Cleanup(pool.Close)

	schema, err := postgres.SchemaSQL()
	if err != nil {
		t.Fatalf("failed to read schema: %v", err)
	}
	if _, err := pool.Exec(ctx, schema); err != nil {
		t.Fatalf("failed to apply schema: %v", err)
	}

	return pool
}

func TestRepository_CreateAndGet(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	ctx := context.Background()

	acct := &domain.Account{
		CustomerID: "11111111-1111-1111-1111-111111111111",
		Type:       domain.CheckingAccount,
		Status:     domain.StatusActive,
		Balance:    &commonv1.Money{CurrencyCode: "USD", MinorUnits: 10000},
	}

	if err := repo.Create(ctx, acct); err != nil {
		t.Fatalf("Create() error = %v", err)
	}
	if acct.ID == "" {
		t.Fatal("expected Create to populate ID")
	}

	got, err := repo.Get(ctx, acct.ID)
	if err != nil {
		t.Fatalf("Get() error = %v", err)
	}
	if got.Balance.MinorUnits != 10000 || got.Balance.CurrencyCode != "USD" {
		t.Fatalf("got balance %+v, want 10000 USD", got.Balance)
	}
}

func TestRepository_UpdateBalance_IdempotentReplay(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	ctx := context.Background()

	acct := &domain.Account{
		CustomerID: "11111111-1111-1111-1111-111111111111",
		Type:       domain.CheckingAccount,
		Status:     domain.StatusActive,
		Balance:    &commonv1.Money{CurrencyCode: "USD", MinorUnits: 1000},
	}
	if err := repo.Create(ctx, acct); err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	updated, replayed, err := repo.UpdateBalance(ctx, acct.ID, -300, "key-1")
	if err != nil {
		t.Fatalf("UpdateBalance() error = %v", err)
	}
	if replayed {
		t.Fatal("expected first call to not be a replay")
	}
	if updated.Balance.MinorUnits != 700 {
		t.Fatalf("got balance %d, want 700", updated.Balance.MinorUnits)
	}

	updated2, replayed2, err := repo.UpdateBalance(ctx, acct.ID, -999999, "key-1")
	if err != nil {
		t.Fatalf("UpdateBalance() replay error = %v", err)
	}
	if !replayed2 {
		t.Fatal("expected second call with same idempotency key to be a replay")
	}
	if updated2.Balance.MinorUnits != 700 {
		t.Fatalf("replay should return unchanged balance 700, got %d", updated2.Balance.MinorUnits)
	}
}

func TestRepository_UpdateBalance_ConcurrentDeltasDoNotLoseUpdates(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	ctx := context.Background()

	acct := &domain.Account{
		CustomerID: "11111111-1111-1111-1111-111111111111",
		Type:       domain.CheckingAccount,
		Status:     domain.StatusActive,
		Balance:    &commonv1.Money{CurrencyCode: "USD", MinorUnits: 10000},
	}
	if err := repo.Create(ctx, acct); err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	// Pre-warm the pool to 2 live connections. Otherwise, whichever goroutine
	// doesn't reuse the connection from Create above pays a connection
	// handshake that can take longer than this whole UpdateBalance call,
	// serializing the two goroutines and masking the race being tested for.
	conn1, err := pool.Acquire(ctx)
	if err != nil {
		t.Fatalf("Acquire() error = %v", err)
	}
	conn2, err := pool.Acquire(ctx)
	if err != nil {
		t.Fatalf("Acquire() error = %v", err)
	}
	conn1.Release()
	conn2.Release()

	// A start barrier forces both goroutines to call UpdateBalance at the
	// same instant, maximizing the chance their transactions truly overlap
	// instead of relying on scheduling luck.
	var ready sync.WaitGroup
	ready.Add(2)
	start := make(chan struct{})

	var wg sync.WaitGroup
	wg.Add(2)

	var debitErr, creditErr error
	go func() {
		defer wg.Done()
		ready.Done()
		<-start
		_, _, debitErr = repo.UpdateBalance(ctx, acct.ID, -1000, "concurrent-debit")
	}()
	go func() {
		defer wg.Done()
		ready.Done()
		<-start
		_, _, creditErr = repo.UpdateBalance(ctx, acct.ID, 500, "concurrent-credit")
	}()

	ready.Wait()
	close(start)
	wg.Wait()

	if debitErr != nil {
		t.Fatalf("debit UpdateBalance() error = %v", debitErr)
	}
	if creditErr != nil {
		t.Fatalf("credit UpdateBalance() error = %v", creditErr)
	}

	got, err := repo.Get(ctx, acct.ID)
	if err != nil {
		t.Fatalf("Get() error = %v", err)
	}
	if got.Balance.MinorUnits != 9500 {
		t.Fatalf("got balance %d, want 9500 (neither update should be lost)", got.Balance.MinorUnits)
	}
}

func TestRepository_UpdateBalance_InsufficientFunds(t *testing.T) {
	pool := setupTestDB(t)
	repo := postgres.NewRepository(pool)
	ctx := context.Background()

	acct := &domain.Account{
		CustomerID: "11111111-1111-1111-1111-111111111111",
		Type:       domain.CheckingAccount,
		Status:     domain.StatusActive,
		Balance:    &commonv1.Money{CurrencyCode: "USD", MinorUnits: 100},
	}
	if err := repo.Create(ctx, acct); err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	_, _, err := repo.UpdateBalance(ctx, acct.ID, -500, "key-insufficient")
	if !errors.Is(err, postgres.ErrInsufficientFunds) {
		t.Fatalf("UpdateBalance() error = %v, want ErrInsufficientFunds", err)
	}

	got, err := repo.Get(ctx, acct.ID)
	if err != nil {
		t.Fatalf("Get() error = %v", err)
	}
	if got.Balance.MinorUnits != 100 {
		t.Fatalf("got balance %d, want unchanged 100", got.Balance.MinorUnits)
	}
}
