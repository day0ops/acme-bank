// Command server runs the Dashboard BFF, a REST-only aggregation service
// that fans out over gRPC to the Customers, Accounts, Cards, and
// Transactions services, instrumented with OpenTelemetry tracing.
package main

import (
	"context"
	"log"
	"net/http"
	"os"

	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	cardsv1 "example.com/acmebank/proto/gen/go/acmebank/cards/v1"
	customersv1 "example.com/acmebank/proto/gen/go/acmebank/customers/v1"
	transactionsv1 "example.com/acmebank/proto/gen/go/acmebank/transactions/v1"
	"example.com/acmebank/services/dashboard-bff/internal/aggregator"
	"example.com/acmebank/services/dashboard-bff/internal/auth"
	"example.com/acmebank/services/dashboard-bff/internal/clients"
	"example.com/acmebank/services/dashboard-bff/internal/restapi"
)

// defaultJWKSURL points at the acme-bank realm's JWKS endpoint inside the
// cluster, used when JWKS_URL is not set.
const defaultJWKSURL = "http://keycloak.acme-platform.svc.cluster.local:8080/realms/acme-bank/protocol/openid-connect/certs"

func main() {
	ctx := context.Background()

	shutdown, err := setupTracing(ctx)
	if err != nil {
		log.Fatalf("setup tracing: %v", err)
	}
	defer shutdown(ctx)

	dialOpts := []grpc.DialOption{
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithStatsHandler(otelgrpc.NewClientHandler()),
	}

	customersConn, err := grpc.NewClient(envOrDefault("CUSTOMERS_GRPC_ADDR", "customers.acme-bank.svc.cluster.local:9090"), dialOpts...)
	if err != nil {
		log.Fatalf("dial customers: %v", err)
	}
	accountsConn, err := grpc.NewClient(envOrDefault("ACCOUNTS_GRPC_ADDR", "accounts.acme-bank.svc.cluster.local:9090"), dialOpts...)
	if err != nil {
		log.Fatalf("dial accounts: %v", err)
	}
	cardsConn, err := grpc.NewClient(envOrDefault("CARDS_GRPC_ADDR", "cards.acme-bank.svc.cluster.local:9090"), dialOpts...)
	if err != nil {
		log.Fatalf("dial cards: %v", err)
	}
	transactionsConn, err := grpc.NewClient(envOrDefault("TRANSACTIONS_GRPC_ADDR", "transactions.acme-bank.svc.cluster.local:9090"), dialOpts...)
	if err != nil {
		log.Fatalf("dial transactions: %v", err)
	}

	service := aggregator.NewService(
		&clients.GrpcCustomersClient{Stub: customersv1.NewCustomersServiceClient(customersConn)},
		&clients.GrpcAccountsClient{Stub: accountsv1.NewAccountsServiceClient(accountsConn)},
		&clients.GrpcCardsClient{Stub: cardsv1.NewCardsServiceClient(cardsConn)},
		&clients.GrpcTransactionsClient{Stub: transactionsv1.NewTransactionsServiceClient(transactionsConn)},
	)

	jwksURL := envOrDefault("JWKS_URL", defaultJWKSURL)
	verifier, err := auth.NewVerifier(jwksURL)
	if err != nil {
		log.Fatalf("create verifier: %v", err)
	}

	mux := restapi.NewMux(service, verifier)
	log.Println("dashboard-bff HTTP server listening on :8080")
	if err := http.ListenAndServe(":8080", otelhttp.NewHandler(mux, "dashboard-bff")); err != nil {
		log.Fatalf("serve HTTP: %v", err)
	}
}

// envOrDefault returns the environment variable named key, or fallback if unset.
func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// setupTracing configures the global OpenTelemetry tracer provider to export
// spans via OTLP/gRPC, defaulting to a local collector when
// OTEL_EXPORTER_OTLP_ENDPOINT is unset. It returns a shutdown func that
// flushes and closes the exporter.
func setupTracing(ctx context.Context) (func(context.Context) error, error) {
	endpoint := envOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4317")
	exporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpoint(endpoint), otlptracegrpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	res, err := resource.New(ctx, resource.WithAttributes(semconv.ServiceName("dashboard-bff")))
	if err != nil {
		return nil, err
	}
	tp := sdktrace.NewTracerProvider(sdktrace.WithBatcher(exporter), sdktrace.WithResource(res))
	otel.SetTracerProvider(tp)
	return tp.Shutdown, nil
}
