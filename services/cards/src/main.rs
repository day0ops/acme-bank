use cards::auth::Verifier;
use cards::grpc::CardsGrpcService;
use cards::proto::acmebank::cards::v1::cards_service_server::CardsServiceServer;
use cards::repository::Repository;
use cards::{rest, telemetry};
use sqlx::PgPool;
use std::sync::Arc;

const DEFAULT_JWKS_URL: &str =
    "http://keycloak.acme-platform.svc.cluster.local:8080/realms/acme-bank/protocol/openid-connect/certs";

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    telemetry::init_tracing();

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://cards:cards@localhost:5432/cards".to_string());
    let pool = PgPool::connect(&database_url).await?;
    // The migration file has multiple statements; `sqlx::query` uses the
    // prepared-statement protocol which rejects those, so use `raw_sql`
    // (simple query protocol) here instead.
    sqlx::raw_sql(include_str!("../migrations/0001_init.sql"))
        .execute(&pool)
        .await?;

    let jwks_url = std::env::var("JWKS_URL").unwrap_or_else(|_| DEFAULT_JWKS_URL.to_string());
    let verifier = Arc::new(Verifier::from_jwks_url(&jwks_url).await?);

    // Each transport gets its own service instance since the generated gRPC
    // server requires the bare type (not `Arc<T>`) while the REST router
    // needs `Arc<T>` to share state across axum handlers. Both share the
    // same underlying pool, which is cheap to clone (sqlx pools are an Arc
    // handle internally) and safe to use concurrently.
    let rest_service = Arc::new(CardsGrpcService::new(pool.clone(), Repository));
    let grpc_service = CardsGrpcService::new(pool, Repository);

    let grpc_addr = "0.0.0.0:9090".parse()?;
    let grpc_server = tonic::transport::Server::builder()
        .add_service(CardsServiceServer::new(grpc_service))
        .serve(grpc_addr);

    let app = rest::router(rest_service, verifier);
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await?;
    let http_server = axum::serve(listener, app);

    tracing::info!("cards service listening: gRPC on :9090, HTTP on :8080");

    tokio::select! {
        result = grpc_server => result?,
        result = http_server => result?,
    }

    Ok(())
}
