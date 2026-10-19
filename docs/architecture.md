# Acme Bank Platform Foundation - Design

## Purpose

Acme Bank is a polyglot, core-banking microservices demo. It serves two audiences
at once: customer-facing demos/workshops (the same role Istio's Bookinfo sample
plays for sidecar mode, but for ambient mesh and kgateway) and an internal
reference architecture that others can copy patterns from. It is not intended to
become production banking software.

Distributed tracing is a first-class requirement, not an afterthought: every
service is instrumented from the start, and the design calls out exactly where
trace context does and doesn't propagate automatically.

## Scope of this document

The full platform spans more independent pieces than fit in one spec:

1. **Platform foundation** (this document) - service boundaries, languages,
   communication patterns, auth, gateway routing, data layer, observability,
   repo layout
2. Core domain services - detailed implementation plans per service
3. Frontend UI - detailed visual/implementation design
4. Ambient mesh & platform ops - namespaces, waypoints, authorization
   policies, mTLS detail (intentionally kept light here; expanded later)

This document covers #1. Items #2-4 get their own implementation plans once
this foundation is approved.

## Services

| Service | Language | Role | Data store |
|---|---|---|---|
| Customers | Java (Spring Boot) | Customer profiles, KYC-lite fields | PostgreSQL |
| Accounts | Go | Account creation, balances, account types | PostgreSQL |
| Transactions/Ledger | Java (Spring Boot) | Ledger entries, transfers, saga orchestrator | PostgreSQL |
| Cards | Rust | Card issuance, status/lifecycle | PostgreSQL |
| Notifications | Rust | Consumes Redpanda events, pushes live updates to the UI | none (stateless consumer) |
| Fraud/Risk | Java (Spring Boot) | Consumes transaction events, rule-based scoring | PostgreSQL |
| Dashboard (BFF) | Go | Aggregates Customers + Accounts + Transactions + Cards for one composite view | none (aggregator only) |

Each service owns its own PostgreSQL instance. No shared database, no
cross-service joins. Language distribution: Java x3 (system-of-record and
rules-heavy services), Go x2 (fast CRUD + I/O-bound fan-out), Rust x2
(security-sensitive Cards, lightweight async event consumer).

## Service-to-service communication

**Synchronous:** REST/JSON at the edge (kgateway to services, for browser
consumption and natural fit with Gateway API HTTPRoutes). gRPC between
services, using shared Protobuf contracts (strongly typed across Java/Go/Rust,
HTTP/2, plays well with ambient mesh's L7 processing).

Two concrete call paths:

1. **Dashboard BFF → Customers, Accounts, Transactions, Cards** - read-only
   fan-out to build the composite dashboard view. One UI request becomes
   4 parallel backend spans; this is the richest trace in the system.
2. **Transactions (saga orchestrator) → Accounts** - a transfer is: debit
   source account, credit destination account, write ledger entries. If the
   credit step fails, Transactions issues a compensating debit-reversal to the
   source account. Orchestrated (not choreographed), so there is one clear
   owner and a linear trace, useful for demoing retries/fault injection/
   circuit breaking in the mesh.

**Asynchronous (Redpanda, via the outbox pattern):** Transactions writes a
`TransactionCompleted` event to an outbox table in the same DB transaction as
the ledger write; a relay publishes it to Redpanda. Fraud/Risk and
Notifications both consume it independently. Fraud/Risk may publish
`TransactionFlagged`, which Notifications also consumes. Cards publishes
`CardIssued`/`CardStatusChanged`, consumed by Notifications. No service calls
Notifications or Fraud/Risk directly - they only react to events.

Redpanda (not Kafka) was chosen for the broker: Kafka-API compatible, so
client libraries and demo narrative are unchanged, but ships as a single
lightweight binary with no ZooKeeper/KRaft cluster to operate.

## Multi-tenancy & auth

Single tenant: one bank, "Acme Bank." (Multi-tenancy was considered but
dropped in favor of simplicity - see "Deferred" below.)

**Roles** (three-tier RBAC):

| Role | Scope |
|---|---|
| `customer` | Self-service only: view/update own profile, view own accounts/cards/transaction history, initiate transfers from own accounts, request card freeze. No deletes. |
| `banker` | Day-to-day teller operations: search/view any customer, onboard new customers, open accounts, process transfers/deposits on a customer's behalf, issue cards, freeze/unfreeze any card. No deletes, no managing other staff, no system config. |
| `admin` | Everything a banker can do, plus: deactivate/delete customers and accounts, manage banker/admin user accounts, override fraud flags, full audit log access. |

Modeled in Keycloak as composite roles - `admin` includes all `banker`
permissions.

**IdP:** Keycloak, one realm (no realm-per-tenant, since there is only one
tenant). **Token flow:** the UI performs OIDC Authorization Code + PKCE
against Keycloak, attaches the resulting JWT as a Bearer token to kgateway.
kgateway validates the JWT (signature, issuer, expiry) and can enforce
coarse route-level checks (e.g. only `banker`/`admin` may hit `/admin/*`).
Downstream services trust the forwarded identity and apply any additional
fine-grained checks themselves (e.g. "is this caller the account owner, or at
least a banker?"). This sets up a layered-defense story: kgateway enforces
authN/coarse authZ at the edge; an Istio `AuthorizationPolicy` in ambient mesh
can additionally enforce east-west rules (e.g. only the Dashboard BFF may call
Accounts' internal read endpoint) - full detail deferred to the ambient mesh
sub-project.

**Guest (unauthenticated) mode:** since this app doubles as a live demo tool,
guest access matters beyond a generic marketing page:

- Public landing page for Acme Bank
- A read-only sandbox dashboard with seeded demo data (fake customer,
  accounts, transactions, cards), so the whole UI can be walked through live
  without logging in first
- A "Log in" / "Open an account" CTA into the real Keycloak flow

## Gateway routing & mesh topology

**kgateway HTTPRoutes** (one Gateway resource, path-based):

- `/` → Web UI
- `/api/customers/*`, `/api/accounts/*`, `/api/transactions/*`,
  `/api/cards/*` → respective service (direct CRUD)
- `/api/dashboard` → Dashboard BFF (composite view)
- `/api/notifications` → Notifications (live WebSocket/REST feed for the UI)
- `/admin/*` → same services, routes tagged with a JWT role check
  (banker/admin only)
- Keycloak exposed on its own route for the login/token flow

**Mesh topology:** kept intentionally light in this document. Two namespaces:
`acme-bank` for domain services + UI, `acme-platform` for Keycloak, Redpanda,
Postgres instances, and the observability stack. This is enough to
demonstrate ambient's cross-namespace mTLS without expanding
namespace/waypoint policy design into its own effort here - fine-grained
waypoint and `AuthorizationPolicy` choices belong to the later "ambient mesh
& platform ops" sub-project.

## Observability & tracing

- **Instrumentation:** every service uses the OpenTelemetry SDK for its
  language (Java: OTel Java agent / Spring Boot starter; Go: otel-go with
  net/http + gRPC instrumentation; Rust: `tracing` crate +
  `opentelemetry-rust`). All export via OTLP to an OpenTelemetry Collector.
- **Trace backend:** Grafana Tempo.
- **Metrics:** Prometheus + Grafana dashboards (ambient mesh exposes
  Envoy/ztunnel metrics natively).
- **Mesh visualization:** Kiali, for the service graph.
- **Async trace propagation:** sync calls (HTTP through kgateway, gRPC
  between services) get trace context propagated for free by the mesh. Async
  calls over Redpanda do not - each publisher must inject the W3C
  `traceparent` into message headers and each consumer must extract it, or
  the trace breaks at that hop. This is called out explicitly as a real
  distributed-tracing consideration, not just a mesh feature to demo.

## Repo layout & shared contracts

```
acme-bank/
├── services/
│   ├── customers/        (Java/Spring Boot)
│   ├── accounts/         (Go)
│   ├── transactions/     (Java/Spring Boot)
│   ├── cards/             (Rust)
│   ├── notifications/    (Rust)
│   ├── fraud-risk/       (Java/Spring Boot)
│   └── dashboard-bff/    (Go)
├── web/                  (React + TS UI)
├── proto/                (shared gRPC + event schemas, one IDL for both)
├── platform/
│   ├── keycloak/         (realm export/config)
│   ├── redpanda/
│   ├── observability/    (Tempo, Prometheus, Grafana, Kiali config)
│   └── gateway/          (kgateway Gateway/HTTPRoute manifests)
├── deploy/
│   └── helm/             (per-service charts + one umbrella chart)
└── docs/
```

Monorepo. Each service is self-contained with its own Dockerfile and native
build tooling (Maven/Gradle, go.mod, Cargo.toml), but all share `proto/` for
gRPC contracts **and** Redpanda event payload schemas - one IDL (Protobuf)
instead of separate REST DTOs, gRPC contracts, and event schemas drifting
apart independently. Helm charts for deployment (per-service chart plus an
umbrella chart to install everything together), so toggling optional pieces
or swapping components (e.g. Tempo for Jaeger) later is a values.yaml change.

## UI stack

- React + TypeScript + Vite, styled with Tailwind CSS and shadcn/ui
  components
- Framer Motion for animations/transitions
- TanStack Query for server state/data fetching; Keycloak's JS adapter (or
  oidc-client-ts) for the PKCE login flow
- Sonner for toast notifications, fed by a WebSocket connection to the
  Notifications service for live updates
- Pages: public landing (guest) with the read-only sandbox dashboard, login,
  customer dashboard (self-service), banker console (customer search +
  operations), admin console (staff management), account/transaction/card
  detail views

Pixel-level visual design (logo, color system, layout polish) is
implementation-time work for a dedicated design pass, not decided in this
architecture spec.

## Testing & error handling conventions

- **Unit tests:** native per language (JUnit5 for Java, `go test` + testify
  for Go, built-in `#[test]` for Rust)
- **Integration tests:** Testcontainers (Postgres + Redpanda) per service -
  real dependencies, not mocks, consistent across all three languages
- **E2E:** a Playwright suite against the running stack, covering login as
  each role, account creation, a transfer (including a forced-failure case to
  exercise the saga's compensating action), and the live dashboard
- **Error responses:** RFC 9457 Problem Details for REST; standard gRPC
  status codes + `google.rpc.Status` details internally
- **Idempotency:** mutating endpoints that move money (transfers, deposits)
  require an idempotency key, so a client retry after a timeout cannot
  double-process a transfer

## Deferred / explicitly out of scope

- **Multi-tenancy:** considered (realm-per-tenant or a shared realm with a
  `tenant_id` claim), dropped in favor of a single tenant ("Acme Bank") with
  role-based access control instead. Revisit only if a specific demo needs
  tenant isolation.
- **agentgateway / AI-agent feature:** considered as a second gateway for an
  MCP-based assistant feature, explicitly deferred - no AI-agent feature is
  in scope for this design.
- **Realm-per-tenant Keycloak setup:** not needed given single-tenant scope.
- **Fine-grained ambient mesh policy** (waypoints, `AuthorizationPolicy`
  detail, namespace-level mTLS configuration): deferred to a dedicated
  "ambient mesh & platform ops" sub-project.
- **Loans, payees/beneficiaries, statements/reporting:** possible future
  domain services if the demo needs more surface area later.
