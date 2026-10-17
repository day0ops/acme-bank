// Command server runs the Accounts service, exposing both a gRPC and a REST
// API backed by PostgreSQL, instrumented with OpenTelemetry tracing.
package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"google.golang.org/grpc"

	accountsv1 "example.com/acmebank/proto/gen/go/acmebank/accounts/v1"
	"example.com/acmebank/services/accounts/internal/grpcapi"
	"example.com/acmebank/services/accounts/internal/postgres"
	"example.com/acmebank/services/accounts/internal/restapi"
)

func main() {
	ctx := context.Background()

	shutdown, err := setupTracing(ctx)
	if err != nil {
		log.Fatalf("setup tracing: %v", err)
	}
	defer shutdown(ctx)

	dbURL := os.Getenv("DATABASE_URL")
	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		log.Fatalf("connect to database: %v", err)
	}
	defer pool.Close()

	schema, _ := postgres.SchemaSQL()
	if _, err := pool.Exec(ctx, schema); err != nil {
		log.Fatalf("apply schema: %v", err)
	}

	repo := postgres.NewRepository(pool)
	server := grpcapi.NewServer(repo)

	go func() {
		lis, err := net.Listen("tcp", ":9090")
		if err != nil {
			log.Fatalf("listen on :9090: %v", err)
		}
		grpcServer := grpc.NewServer(grpc.StatsHandler(otelgrpc.NewServerHandler()))
		accountsv1.RegisterAccountsServiceServer(grpcServer, server)
		log.Println("gRPC server listening on :9090")
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatalf("serve gRPC: %v", err)
		}
	}()

	mux := restapi.NewMux(server)
	log.Println("HTTP server listening on :8080")
	if err := http.ListenAndServe(":8080", otelhttp.NewHandler(mux, "accounts")); err != nil {
		log.Fatalf("serve HTTP: %v", err)
	}
}

// setupTracing configures the global OpenTelemetry tracer provider to export
// spans via OTLP/gRPC, defaulting to a local collector when
// OTEL_EXPORTER_OTLP_ENDPOINT is unset. It returns a shutdown func that
// flushes and closes the exporter.
func setupTracing(ctx context.Context) (func(context.Context) error, error) {
	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if endpoint == "" {
		endpoint = "localhost:4317"
	}
	exporter, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpoint(endpoint), otlptracegrpc.WithInsecure())
	if err != nil {
		return nil, err
	}
	res, err := resource.New(ctx, resource.WithAttributes(semconv.ServiceName("accounts")))
	if err != nil {
		return nil, err
	}
	tp := sdktrace.NewTracerProvider(sdktrace.WithBatcher(exporter), sdktrace.WithResource(res))
	otel.SetTracerProvider(tp)
	return tp.Shutdown, nil
}
