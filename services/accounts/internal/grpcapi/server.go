// Package grpcapi implements the AccountsService gRPC server on top of the
// postgres repository.
package grpcapi

import (
	"context"
	"errors"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	"example.com/acmebank/services/accounts/internal/domain"
	"example.com/acmebank/services/accounts/internal/postgres"
)

// Server implements accountsv1.AccountsServiceServer on top of a
// postgres.Repository.
type Server struct {
	accountsv1.UnimplementedAccountsServiceServer
	repo *postgres.Repository
}

// NewServer returns a Server backed by repo.
func NewServer(repo *postgres.Repository) *Server {
	return &Server{repo: repo}
}

// CreateAccount creates a new account with a zero balance in req.CurrencyCode.
func (s *Server) CreateAccount(ctx context.Context, req *accountsv1.CreateAccountRequest) (*accountsv1.CreateAccountResponse, error) {
	acct := &domain.Account{
		CustomerID: req.CustomerId,
		Type:       domain.AccountType(req.Type - 1),
		Status:     domain.StatusActive,
		Balance:    &commonv1.Money{CurrencyCode: req.CurrencyCode, MinorUnits: 0},
	}
	if err := s.repo.Create(ctx, acct); err != nil {
		return nil, status.Errorf(codes.Internal, "create account: %v", err)
	}
	return &accountsv1.CreateAccountResponse{Account: toProto(acct)}, nil
}

// GetAccount returns the account with the given ID.
func (s *Server) GetAccount(ctx context.Context, req *accountsv1.GetAccountRequest) (*accountsv1.GetAccountResponse, error) {
	acct, err := s.repo.Get(ctx, req.Id)
	if err != nil {
		if errors.Is(err, postgres.ErrNotFound) {
			return nil, status.Errorf(codes.NotFound, "account %s not found", req.Id)
		}
		return nil, status.Errorf(codes.Internal, "get account: %v", err)
	}
	return &accountsv1.GetAccountResponse{Account: toProto(acct)}, nil
}

// ListAccountsByCustomer returns all accounts owned by req.CustomerId.
func (s *Server) ListAccountsByCustomer(ctx context.Context, req *accountsv1.ListAccountsByCustomerRequest) (*accountsv1.ListAccountsByCustomerResponse, error) {
	accounts, err := s.repo.ListByCustomer(ctx, req.CustomerId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "list accounts: %v", err)
	}
	resp := &accountsv1.ListAccountsByCustomerResponse{}
	for _, a := range accounts {
		resp.Accounts = append(resp.Accounts, toProto(a))
	}
	return resp, nil
}

// Debit and Credit validate currency against the account's current balance,
// then delegate the actual balance change to the repository as an atomic
// signed delta (see Task 2's UpdateBalance) rather than computing an
// absolute new balance here - computing it here would race against
// concurrent operations on the same account.

// Debit subtracts req.Amount from the account's balance. It fails with
// FailedPrecondition if currencies differ or the debit would overdraw the
// account.
func (s *Server) Debit(ctx context.Context, req *accountsv1.DebitRequest) (*accountsv1.DebitResponse, error) {
	acct, err := s.repo.Get(ctx, req.AccountId)
	if err != nil {
		if errors.Is(err, postgres.ErrNotFound) {
			return nil, status.Errorf(codes.NotFound, "account %s not found", req.AccountId)
		}
		return nil, status.Errorf(codes.Internal, "get account: %v", err)
	}
	if req.Amount.CurrencyCode != acct.Balance.CurrencyCode {
		return nil, status.Errorf(codes.FailedPrecondition, "%v: account is %s, amount is %s", domain.ErrCurrencyMismatch, acct.Balance.CurrencyCode, req.Amount.CurrencyCode)
	}
	updated, _, err := s.repo.UpdateBalance(ctx, req.AccountId, -req.Amount.MinorUnits, req.IdempotencyKey)
	if err != nil {
		if errors.Is(err, postgres.ErrInsufficientFunds) {
			return nil, status.Errorf(codes.FailedPrecondition, "%v", err)
		}
		return nil, status.Errorf(codes.Internal, "update balance: %v", err)
	}
	return &accountsv1.DebitResponse{Account: toProto(updated)}, nil
}

// Credit adds req.Amount to the account's balance. It fails with
// FailedPrecondition if currencies differ.
func (s *Server) Credit(ctx context.Context, req *accountsv1.CreditRequest) (*accountsv1.CreditResponse, error) {
	acct, err := s.repo.Get(ctx, req.AccountId)
	if err != nil {
		if errors.Is(err, postgres.ErrNotFound) {
			return nil, status.Errorf(codes.NotFound, "account %s not found", req.AccountId)
		}
		return nil, status.Errorf(codes.Internal, "get account: %v", err)
	}
	if req.Amount.CurrencyCode != acct.Balance.CurrencyCode {
		return nil, status.Errorf(codes.FailedPrecondition, "%v: account is %s, amount is %s", domain.ErrCurrencyMismatch, acct.Balance.CurrencyCode, req.Amount.CurrencyCode)
	}
	updated, _, err := s.repo.UpdateBalance(ctx, req.AccountId, req.Amount.MinorUnits, req.IdempotencyKey)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "update balance: %v", err)
	}
	return &accountsv1.CreditResponse{Account: toProto(updated)}, nil
}

func toProto(a *domain.Account) *accountsv1.Account {
	return &accountsv1.Account{
		Id:         a.ID,
		CustomerId: a.CustomerID,
		Type:       accountsv1.AccountType(a.Type + 1),
		Status:     accountsv1.AccountStatus(a.Status + 1),
		Balance:    a.Balance,
	}
}
