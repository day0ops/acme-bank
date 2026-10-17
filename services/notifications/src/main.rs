use notifications::auth::Verifier;
use notifications::store::NotificationStore;
use notifications::{consumer, rest, telemetry};
use std::sync::Arc;

const DEFAULT_JWKS_URL: &str =
    "http://keycloak.acme-platform.svc.cluster.local:8080/realms/acme-bank/protocol/openid-connect/certs";

#[tokio::main]
async fn main() {
    telemetry::init_tracing();

    let store = Arc::new(NotificationStore::new());
    let kafka_brokers =
        std::env::var("KAFKA_BOOTSTRAP_SERVERS").unwrap_or_else(|_| "localhost:9092".to_string());

    let consumer_store = store.clone();
    tokio::spawn(async move {
        consumer::run(consumer_store, &kafka_brokers).await;
    });

    let jwks_url = std::env::var("JWKS_URL").unwrap_or_else(|_| DEFAULT_JWKS_URL.to_string());
    let verifier = Arc::new(
        Verifier::from_jwks_url(&jwks_url)
            .await
            .expect("failed to fetch JWKS"),
    );

    let app = rest::router(store, verifier);
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.unwrap();
    tracing::info!("notifications service listening on :8080");
    axum::serve(listener, app).await.unwrap();
}
