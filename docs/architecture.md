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
`TransactionFlagged`, which Notifications also consumes. No service calls
Notifications or Fraud/Risk directly - they only react to events.

Cards publishing `CardIssued`/`CardStatusChanged` for Notifications to
consume was part of the original design but has not been implemented (see
"Known gaps" below) - Cards currently has no Kafka producer at all.

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
- `/api/notifications` → Notifications (REST feed for the UI; the `/ws`
  WebSocket endpoint is not yet routed through the gateway - see "Known gaps")
- `/admin/*` → same services, routes tagged with a JWT role check
  (banker/admin only) - not yet implemented; authZ is currently enforced
  per-service only, not at the edge (see "Known gaps")
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
  This client-side instrumentation is complete in all 7 services.
- **Trace backend:** Grafana Tempo (planned). **Not yet deployed** - no OTel
  Collector or Tempo instance runs anywhere in this repo's manifests today,
  so every service's OTLP export currently has nowhere to land. See "Known
  gaps" below.
- **Metrics:** Prometheus + Grafana dashboards (ambient mesh exposes
  Envoy/ztunnel metrics natively). Not yet deployed, same gap as above.
- **Mesh visualization:** Kiali, for the service graph. Not yet deployed.
- **Async trace propagation:** sync calls (HTTP through kgateway, gRPC
  between services) get trace context propagated for free by the mesh. Async
  calls over Redpanda do not - each publisher injects the W3C `traceparent`
  into message headers and each consumer extracts and continues it, or the
  trace breaks at that hop. This propagation is implemented end-to-end for
  the `transactions.events` → Fraud/Risk and Notifications hops, and for
  Fraud/Risk's own `fraud.events` republish. It does not yet extend back to
  the original HTTP request that triggered the outbox write (the relay
  starts a fresh span rather than continuing the request's trace, since
  that would require persisting trace context at outbox-write time) - this
  remains a known limitation, not a bug to be confused with the hop-to-hop
  propagation above, which does work.

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
│   └── k8s/              (namespaces, Gateway/HTTPRoutes, AuthorizationPolicy)
├── deploy/
│   ├── helm/             (per-service charts)
│   ├── kind-config.yaml
│   └── scripts/          (kind + ambient mesh + kgateway bring-up)
└── docs/
```

Monorepo. Each service is self-contained with its own Dockerfile and native
build tooling (Maven/Gradle, go.mod, Cargo.toml), but all share `proto/` for
gRPC contracts **and** Redpanda event payload schemas - one IDL (Protobuf)
instead of separate REST DTOs, gRPC contracts, and event schemas drifting
apart independently. Helm charts for deployment, one per service - no
umbrella chart exists yet (see "Known gaps"), so `deploy/scripts/bring-up.sh`
installs the platform scaffolding and each service chart is installed
individually.

There is no `platform/observability/` or `platform/redpanda/` directory -
Redpanda has its own chart under `deploy/helm/redpanda/`, and no
observability backend is deployed yet (see "Known gaps").

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
- **E2E:** a Playwright suite against the running stack. Currently covers the
  guest sandbox flow and the Keycloak login redirect only - authenticated
  flows (account creation, a transfer, the saga's forced-failure/compensation
  case, the live dashboard) are not yet covered. See "Known gaps".
- **Error responses:** RFC 9457 Problem Details for REST, applied
  consistently across all 7 services including auth rejections (401/403);
  standard gRPC status codes + `google.rpc.Status` details internally
- **Idempotency:** mutating endpoints that move money (transfers, deposits)
  were intended to require an idempotency key, so a client retry after a
  timeout cannot double-process a transfer. **Not yet implemented** - see
  "Known gaps".

## Known gaps (not yet implemented)

Unlike "Deferred" below, these are things this document describes as part of
the design but that the current implementation does not yet deliver. Each
was identified during the final whole-branch review and is tracked here
rather than silently left for someone to rediscover:

- **No observability backend deployed.** Every service's OTLP export has
  nowhere to land - no OTel Collector, Tempo, Prometheus, or Kiali instance
  exists in this repo's manifests. Client-side instrumentation is complete;
  only the backend is missing.
- **No idempotency-key support.** `POST /transactions` and the gRPC
  `TransferRequest` accept no client-supplied idempotency key, so a client
  retry after a timeout can double-process a transfer. The saga's own
  internal step keys only protect against double-execution within a single
  saga run, not across separate client retries.
- **No umbrella Helm chart.** Each service has its own chart; there is no
  single chart that installs the whole platform together. `make cluster-up`
  / `deploy/scripts/bring-up.sh` installs the platform scaffolding (mesh,
  kgateway, namespaces, routing), but each service is still installed
  individually.
- **Most per-service Postgres instances have no deployment manifest.** Only
  Accounts' (`platform/k8s/accounts-postgres.yaml`, added for the gateway
  smoke test) exists; Customers, Transactions, Fraud-Risk, and Cards have no
  equivalent yet.
- **Cards → Notifications event path is unimplemented.** The design calls
  for Cards to publish `CardIssued`/`CardStatusChanged` for Notifications to
  consume. Cards has no Kafka producer, no such proto messages exist, and
  Notifications only subscribes to `transactions.events`/`fraud.events`.
- **`/admin/*` route does not exist**, and no edge-level JWT/role
  enforcement runs in kgateway. Every service enforces its own
  authentication and role checks directly; there is no coarse gateway-level
  check in front of them.
- **Notifications' `/ws` WebSocket endpoint has no HTTPRoute** - only the
  REST `/api/notifications` path is reachable through the gateway.
- **E2E coverage is narrower than this document implies** - see "Testing &
  error handling conventions" above.
- **Ownership-based authorization is deferred everywhere.** Every service
  checks role membership (customer/banker/admin) but not whether a given
  customer is the owner of the specific account/card/transaction being
  accessed. Acceptable for a demo with a small, known dataset; would need
  addressing before any real multi-customer use.
- **The Web UI has no Keycloak-subject-to-customer-ID mapping.** The
  authenticated dashboard currently uses a placeholder customer ID for every
  logged-in user rather than resolving the signed-in Keycloak subject to its
  corresponding Customer record.

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
