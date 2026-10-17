// REST response shapes for every backend service the web app talks to.
// Each type is cross-checked against the service's actual DTO/mapping code
// (not assumed from the proto definitions), because each service's REST
// layer was built independently and some serialize enums differently. See
// the enum-format note on `Transaction` below for the one that matters most.

export interface Customer {
  id: string
  firstName: string
  lastName: string
  email: string
  dateOfBirth: string
}

export interface Money {
  currencyCode: string
  minorUnits: number
}

// accounts/internal/restapi/dto.go toAccountResponse: Type/Status call the
// generated proto enum's .String(), which renders the full constant name.
export interface Account {
  id: string
  customerId: string
  type: 'ACCOUNT_TYPE_CHECKING' | 'ACCOUNT_TYPE_SAVINGS' | 'ACCOUNT_TYPE_UNSPECIFIED'
  status: 'ACCOUNT_STATUS_ACTIVE' | 'ACCOUNT_STATUS_FROZEN' | 'ACCOUNT_STATUS_CLOSED' | 'ACCOUNT_STATUS_UNSPECIFIED'
  balance: Money
}

// cards/src/rest.rs CardResponse::from: status calls the generated proto
// enum's .as_str_name(), which also renders the full constant name.
export interface Card {
  id: string
  accountId: string
  lastFour: string
  status: 'CARD_STATUS_ACTIVE' | 'CARD_STATUS_FROZEN' | 'CARD_STATUS_CLOSED' | 'CARD_STATUS_UNSPECIFIED'
}

// transactions/.../TransactionController.java TransactionResponse.from:
// status calls `tx.status().toString()` on the plain Java domain enum
// `TransactionStatus { PENDING, COMPLETED, FAILED }` (not the proto enum's
// `.name()`), so this is the bare constant name, unlike Account/Card above.
// This is the service's own REST API (GET/POST /transactions); it is a real,
// independently-built DTO, not a mistake to "fix" to match Account/Card.
export interface Transaction {
  id: string
  fromAccountId: string
  toAccountId: string
  currencyCode: string
  amountMinorUnits: number
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  failureReason?: string
}

// dashboard-bff/internal/restapi/dto.go toTransactionResponse: this is a
// *different* DTO than Transaction above, built from the Transactions
// service's gRPC (proto) response rather than its REST layer. It calls
// `t.Status.String()` on the generated proto enum, so - unlike the
// Transactions service's own REST API - the BFF's embedded transaction uses
// the full proto constant name, and it omits currencyCode/amountMinorUnits/
// failureReason entirely (dto.go's transactionResponse struct only has
// id/fromAccountId/toAccountId/status). See self-review: the same logical
// "Transaction" resource has two different wire shapes depending on whether
// it's fetched standalone from the Transactions service or embedded in the
// BFF's dashboard aggregation.
export interface DashboardTransaction {
  id: string
  fromAccountId: string
  toAccountId: string
  status: 'TRANSACTION_STATUS_PENDING' | 'TRANSACTION_STATUS_COMPLETED' | 'TRANSACTION_STATUS_FAILED' | 'TRANSACTION_STATUS_UNSPECIFIED'
}

// dashboard-bff/internal/restapi/dto.go customerResponse: narrower than
// Customer above - the BFF's DTO omits dateOfBirth entirely.
export interface DashboardCustomer {
  id: string
  firstName: string
  lastName: string
  email: string
}

// dashboard-bff/internal/restapi/dto.go accountResponse/cardResponse: narrower
// than Account/Card above - the BFF's embedded DTOs omit customerId/accountId
// entirely (same mismatch class as DashboardTransaction/DashboardCustomer).
export interface DashboardAccount {
  id: string
  type: Account['type']
  status: Account['status']
  balance: Money
}

export interface DashboardCard {
  id: string
  lastFour: string
  status: Card['status']
}

export interface AccountView {
  account: DashboardAccount
  cards: DashboardCard[]
  recentTransactions: DashboardTransaction[]
}

export interface DashboardView {
  customer: DashboardCustomer
  accounts: AccountView[]
}

export interface ProblemDetails {
  type: string
  title: string
  status: number
  detail?: string
}

// notifications/src/domain.rs Notification: #[serde(rename_all = "camelCase")]
// on the struct, so the WebSocket payload is the full record below, not just
// `{ message }` - id/eventType/transactionId/timestamp are always present too.
export interface Notification {
  id: string
  eventType: string
  transactionId: string
  message: string
  timestamp: number
}
