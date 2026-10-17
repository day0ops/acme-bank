package domain_test

import (
	"testing"

	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
	"example.com/acmebank/services/accounts/internal/domain"
)

func TestAccount_Debit_InsufficientFunds(t *testing.T) {
	acct := &domain.Account{
		Balance: &commonv1.Money{CurrencyCode: "USD", MinorUnits: 500},
	}

	err := acct.Debit(&commonv1.Money{CurrencyCode: "USD", MinorUnits: 1000})

	if err == nil {
		t.Fatal("expected error for insufficient funds, got nil")
	}
	if acct.Balance.MinorUnits != 500 {
		t.Fatalf("balance should be unchanged on failed debit, got %d", acct.Balance.MinorUnits)
	}
}

func TestAccount_Debit_Success(t *testing.T) {
	acct := &domain.Account{
		Balance: &commonv1.Money{CurrencyCode: "USD", MinorUnits: 1000},
	}

	if err := acct.Debit(&commonv1.Money{CurrencyCode: "USD", MinorUnits: 300}); err != nil {
		t.Fatalf("Debit() error = %v", err)
	}
	if acct.Balance.MinorUnits != 700 {
		t.Fatalf("got balance %d, want 700", acct.Balance.MinorUnits)
	}
}

func TestAccount_Credit_Success(t *testing.T) {
	acct := &domain.Account{
		Balance: &commonv1.Money{CurrencyCode: "USD", MinorUnits: 1000},
	}

	if err := acct.Credit(&commonv1.Money{CurrencyCode: "USD", MinorUnits: 250}); err != nil {
		t.Fatalf("Credit() error = %v", err)
	}
	if acct.Balance.MinorUnits != 1250 {
		t.Fatalf("got balance %d, want 1250", acct.Balance.MinorUnits)
	}
}

func TestAccount_Debit_CurrencyMismatch(t *testing.T) {
	acct := &domain.Account{
		Balance: &commonv1.Money{CurrencyCode: "USD", MinorUnits: 1000},
	}

	err := acct.Debit(&commonv1.Money{CurrencyCode: "EUR", MinorUnits: 100})

	if err == nil {
		t.Fatal("expected error for currency mismatch, got nil")
	}
}
