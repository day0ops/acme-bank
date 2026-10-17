// Package postgres provides a PostgreSQL-backed implementation of the
// accounts repository.
package postgres

import (
	"context"
	_ "embed"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	"example.com/acmebank/services/accounts/internal/domain"
)

//go:embed schema.sql
var schemaSQL string

// SchemaSQL returns the DDL used to create the accounts and
// balance_operations tables.
func SchemaSQL() (string, error) {
	return schemaSQL, nil
}

// ErrNotFound is returned when a requested account does not exist.
var ErrNotFound = errors.New("account not found")

// ErrInsufficientFunds is returned when applying a balance delta would make
// an account's balance negative.
var ErrInsufficientFunds = errors.New("insufficient funds")

// Repository is a PostgreSQL-backed store for domain.Account.
type Repository struct {
	pool *pgxpool.Pool
}

// NewRepository returns a Repository backed by pool.
func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// Create inserts a into the accounts table and populates its generated ID.
func (r *Repository) Create(ctx context.Context, a *domain.Account) error {
	row := r.pool.QueryRow(ctx,
		`INSERT INTO accounts (customer_id, account_type, status, currency_code, balance_minor)
		 VALUES ($1, $2, $3, $4, $5) RETURNING id`,
		a.CustomerID, int(a.Type), int(a.Status), a.Balance.CurrencyCode, a.Balance.MinorUnits)
	return row.Scan(&a.ID)
}

// Get returns the account with the given id, or ErrNotFound if it does not exist.
func (r *Repository) Get(ctx context.Context, id string) (*domain.Account, error) {
	row := r.pool.QueryRow(ctx,
		`SELECT id, customer_id, account_type, status, currency_code, balance_minor
		 FROM accounts WHERE id = $1`, id)
	return scanAccount(row)
}

// ListByCustomer returns all accounts owned by customerID, ordered by creation time.
func (r *Repository) ListByCustomer(ctx context.Context, customerID string) ([]*domain.Account, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, customer_id, account_type, status, currency_code, balance_minor
		 FROM accounts WHERE customer_id = $1 ORDER BY created_at`, customerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var accounts []*domain.Account
	for rows.Next() {
		acct, err := scanAccount(rows)
		if err != nil {
			return nil, err
		}
		accounts = append(accounts, acct)
	}
	return accounts, rows.Err()
}

// UpdateBalance atomically applies deltaMinorUnits to accountID's balance
// (negative for a debit, positive for a credit) and records idempotencyKey
// so a replayed call is a no-op, so retried requests (e.g. Task 3's
// Debit/Credit RPCs) cannot double-apply a balance change. The delta is
// applied in a single UPDATE statement, so two concurrent calls against the
// same account can never read-then-overwrite each other's result. Returns
// ErrInsufficientFunds if applying a negative delta would make the balance
// negative, or ErrNotFound if the account doesn't exist.
func (r *Repository) UpdateBalance(ctx context.Context, accountID string, deltaMinorUnits int64, idempotencyKey string) (*domain.Account, bool, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return nil, false, err
	}
	// Rollback is a no-op once Commit succeeds; the error is immaterial here.
	defer func() { _ = tx.Rollback(ctx) }()

	_, err = tx.Exec(ctx,
		`INSERT INTO balance_operations (idempotency_key, account_id) VALUES ($1, $2)`,
		idempotencyKey, accountID)
	if err != nil {
		if isUniqueViolation(err) {
			acct, getErr := r.Get(ctx, accountID)
			return acct, true, getErr
		}
		return nil, false, err
	}

	row := tx.QueryRow(ctx,
		`UPDATE accounts
		 SET balance_minor = balance_minor + $1, updated_at = now()
		 WHERE id = $2 AND balance_minor + $1 >= 0
		 RETURNING id, customer_id, account_type, status, currency_code, balance_minor`,
		deltaMinorUnits, accountID)
	acct, err := scanAccount(row)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			// The UPDATE matched zero rows: either the account doesn't exist,
			// or it exists but applying the delta would make the balance
			// negative. Disambiguate with an existence check.
			if _, getErr := r.Get(ctx, accountID); getErr == nil {
				return nil, false, ErrInsufficientFunds
			}
			return nil, false, ErrNotFound
		}
		return nil, false, err
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, false, err
	}
	return acct, false, nil
}

// isUniqueViolation reports whether err is a Postgres unique_violation (23505).
func isUniqueViolation(err error) bool {
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}

// scanner is satisfied by both pgx.Row and pgx.Rows, letting scanAccount
// serve single-row and multi-row queries alike.
type scanner interface {
	Scan(dest ...any) error
}

func scanAccount(row scanner) (*domain.Account, error) {
	var (
		a            domain.Account
		accountType  int
		status       int
		currencyCode string
		balanceMinor int64
	)
	err := row.Scan(&a.ID, &a.CustomerID, &accountType, &status, &currencyCode, &balanceMinor)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("scan account: %w", err)
	}
	a.Type = domain.AccountType(accountType)
	a.Status = domain.AccountStatus(status)
	a.Balance = &commonv1.Money{CurrencyCode: currencyCode, MinorUnits: balanceMinor}
	return &a, nil
}
