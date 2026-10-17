package domain

import (
	"errors"
	"fmt"

	commonv1 "example.com/acmebank/proto/gen/go/acmebank/common/v1"
)

// AccountType identifies the product category of an account.
type AccountType int

const (
	CheckingAccount AccountType = iota
	SavingsAccount
)

// AccountStatus represents the lifecycle state of an account.
type AccountStatus int

const (
	StatusActive AccountStatus = iota
	StatusFrozen
	StatusClosed
)

// Account is the domain representation of a bank account.
type Account struct {
	ID         string
	CustomerID string
	Type       AccountType
	Status     AccountStatus
	Balance    *commonv1.Money
}

// ErrCurrencyMismatch indicates a Debit or Credit amount's currency differs
// from the account balance's currency.
var ErrCurrencyMismatch = errors.New("currency mismatch")

// ErrInsufficientFunds indicates a Debit amount exceeds the account balance.
var ErrInsufficientFunds = errors.New("insufficient funds")

// Debit subtracts amount from the account balance. It returns
// ErrCurrencyMismatch or ErrInsufficientFunds, leaving the balance
// unchanged, if either invariant is violated.
func (a *Account) Debit(amount *commonv1.Money) error {
	if amount.CurrencyCode != a.Balance.CurrencyCode {
		return fmt.Errorf("%w: account is %s, amount is %s", ErrCurrencyMismatch, a.Balance.CurrencyCode, amount.CurrencyCode)
	}
	if a.Balance.MinorUnits < amount.MinorUnits {
		return fmt.Errorf("%w: balance %d, requested %d", ErrInsufficientFunds, a.Balance.MinorUnits, amount.MinorUnits)
	}
	a.Balance.MinorUnits -= amount.MinorUnits
	return nil
}

// Credit adds amount to the account balance. It returns ErrCurrencyMismatch,
// leaving the balance unchanged, if the currencies differ.
func (a *Account) Credit(amount *commonv1.Money) error {
	if amount.CurrencyCode != a.Balance.CurrencyCode {
		return fmt.Errorf("%w: account is %s, amount is %s", ErrCurrencyMismatch, a.Balance.CurrencyCode, amount.CurrencyCode)
	}
	a.Balance.MinorUnits += amount.MinorUnits
	return nil
}
