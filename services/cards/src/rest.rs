use crate::auth::{self, Verifier};
use crate::grpc::CardsGrpcService;
use crate::proto::acmebank::cards::v1::cards_service_server::CardsService as _;
use crate::proto::acmebank::cards::v1::{
    FreezeCardRequest, GetCardRequest, IssueCardRequest, ListCardsByAccountRequest,
    UnfreezeCardRequest,
};
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::middleware;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tonic::{Code, Request};

/// Issuing a card is a banker/admin operation per the RBAC table.
const BANKER_OR_ADMIN: &[&str] = &["banker", "admin"];
/// Empty slice: `auth::require_role` treats this as "any authenticated
/// caller" - a validated token is enough, no specific role is required.
const ANY_AUTHENTICATED: &[&str] = &[];

/// RFC 9457 Problem Details body.
#[derive(Serialize)]
struct Problem {
    #[serde(rename = "type")]
    problem_type: String,
    title: String,
    status: u16,
    detail: String,
}

fn problem_response(status: StatusCode, title: &str, detail: String) -> Response {
    let body = Problem {
        problem_type: "about:blank".to_string(),
        title: title.to_string(),
        status: status.as_u16(),
        detail,
    };
    let mut response = (status, Json(body)).into_response();
    response
        .headers_mut()
        .insert("content-type", "application/problem+json".parse().unwrap());
    response
}

/// Maps a gRPC status from the in-process `CardsGrpcService` to Problem Details.
/// `FailedPrecondition` (e.g. freezing/unfreezing a closed card, per the Task 3
/// invariant) maps to 422, matching the convention used by the Accounts
/// service's REST layer.
fn status_to_problem(status: tonic::Status) -> Response {
    match status.code() {
        Code::NotFound => problem_response(
            StatusCode::NOT_FOUND,
            "Not Found",
            status.message().to_string(),
        ),
        Code::InvalidArgument => problem_response(
            StatusCode::BAD_REQUEST,
            "Bad Request",
            status.message().to_string(),
        ),
        Code::FailedPrecondition => problem_response(
            StatusCode::UNPROCESSABLE_ENTITY,
            "Unprocessable Entity",
            status.message().to_string(),
        ),
        _ => problem_response(
            StatusCode::INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            status.message().to_string(),
        ),
    }
}

#[derive(Deserialize)]
struct IssueCardBody {
    #[serde(rename = "accountId")]
    account_id: String,
}

/// REST representation of a Card. Mirrors the request JSON contract
/// (camelCase keys, string enum value) so a client can round-trip a
/// response back into a request body.
#[derive(Serialize)]
struct CardResponse {
    id: String,
    #[serde(rename = "accountId")]
    account_id: String,
    #[serde(rename = "lastFour")]
    last_four: String,
    status: String,
}

impl From<&crate::proto::acmebank::cards::v1::Card> for CardResponse {
    fn from(card: &crate::proto::acmebank::cards::v1::Card) -> Self {
        use crate::proto::acmebank::cards::v1::CardStatus;
        let status = CardStatus::try_from(card.status)
            .unwrap_or(CardStatus::Unspecified)
            .as_str_name()
            .to_string();
        CardResponse {
            id: card.id.clone(),
            account_id: card.account_id.clone(),
            last_four: card.last_four.clone(),
            status,
        }
    }
}

/// Converts the `Option<Card>` carried by the proto response envelopes into
/// a `CardResponse`. The gRPC service always populates `card` on success, so
/// a `None` here indicates a server bug rather than a client error.
fn into_card_response(card: Option<crate::proto::acmebank::cards::v1::Card>) -> CardResponse {
    CardResponse::from(&card.expect("card response should always include a card"))
}

/// Extractor that behaves like `Json<T>` but reports deserialization
/// failures as RFC 9457 Problem Details instead of axum's default
/// `text/plain` rejection body.
struct ProblemJson<T>(T);

#[async_trait::async_trait]
impl<T, S> axum::extract::FromRequest<S> for ProblemJson<T>
where
    T: serde::de::DeserializeOwned,
    S: Send + Sync,
{
    type Rejection = Response;

    async fn from_request(req: axum::extract::Request, state: &S) -> Result<Self, Self::Rejection> {
        match Json::<T>::from_request(req, state).await {
            Ok(Json(value)) => Ok(ProblemJson(value)),
            Err(rejection) => Err(problem_response(
                StatusCode::BAD_REQUEST,
                "Bad Request",
                rejection.body_text(),
            )),
        }
    }
}

/// Wires the Cards REST API onto `service`, the same `CardsGrpcService`
/// implementation the gRPC transport uses. Every route requires a valid
/// Bearer JWT (checked via `verifier`); `POST /cards` additionally requires
/// the `banker` or `admin` role.
pub fn router(service: Arc<CardsGrpcService>, verifier: Arc<Verifier>) -> Router {
    let cards_route = post(create_card)
        .layer(middleware::from_fn(auth::require_role(
            verifier.clone(),
            BANKER_OR_ADMIN,
        )))
        .get(list_cards);

    Router::new()
        .route("/cards", cards_route)
        .route("/cards/:id", get(get_card))
        .route("/cards/:id/freeze", post(freeze_card))
        .route("/cards/:id/unfreeze", post(unfreeze_card))
        .layer(middleware::from_fn(auth::require_role(
            verifier,
            ANY_AUTHENTICATED,
        )))
        .with_state(service)
}

async fn create_card(
    State(service): State<Arc<CardsGrpcService>>,
    ProblemJson(body): ProblemJson<IssueCardBody>,
) -> Response {
    match service
        .issue_card(Request::new(IssueCardRequest {
            account_id: body.account_id,
        }))
        .await
    {
        Ok(resp) => (
            StatusCode::CREATED,
            Json(into_card_response(resp.into_inner().card)),
        )
            .into_response(),
        Err(status) => status_to_problem(status),
    }
}

async fn get_card(
    State(service): State<Arc<CardsGrpcService>>,
    Path(id): Path<String>,
) -> Response {
    match service.get_card(Request::new(GetCardRequest { id })).await {
        Ok(resp) => Json(into_card_response(resp.into_inner().card)).into_response(),
        Err(status) => status_to_problem(status),
    }
}

async fn list_cards(
    State(service): State<Arc<CardsGrpcService>>,
    Query(params): Query<HashMap<String, String>>,
) -> Response {
    let account_id = match params.get("accountId") {
        Some(id) => id.clone(),
        None => {
            return problem_response(
                StatusCode::BAD_REQUEST,
                "Bad Request",
                "accountId query parameter is required".to_string(),
            )
        }
    };
    match service
        .list_cards_by_account(Request::new(ListCardsByAccountRequest { account_id }))
        .await
    {
        Ok(resp) => Json(
            resp.into_inner()
                .cards
                .iter()
                .map(CardResponse::from)
                .collect::<Vec<_>>(),
        )
        .into_response(),
        Err(status) => status_to_problem(status),
    }
}

async fn freeze_card(
    State(service): State<Arc<CardsGrpcService>>,
    Path(id): Path<String>,
) -> Response {
    match service
        .freeze_card(Request::new(FreezeCardRequest { id }))
        .await
    {
        Ok(resp) => Json(into_card_response(resp.into_inner().card)).into_response(),
        Err(status) => status_to_problem(status),
    }
}

async fn unfreeze_card(
    State(service): State<Arc<CardsGrpcService>>,
    Path(id): Path<String>,
) -> Response {
    match service
        .unfreeze_card(Request::new(UnfreezeCardRequest { id }))
        .await
    {
        Ok(resp) => Json(into_card_response(resp.into_inner().card)).into_response(),
        Err(status) => status_to_problem(status),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::test_support::TestKeyPair;
    use crate::repository::Repository;
    use axum::body::Body;
    use axum::http::{Request as HttpRequest, StatusCode};
    use sqlx::PgPool;
    use testcontainers::{runners::AsyncRunner, ImageExt};
    use testcontainers_modules::postgres::Postgres;
    use tower::ServiceExt;

    /// Builds the router against a fresh Postgres container and a
    /// `Verifier` backed by a locally-generated key pair. Returns the key
    /// pair so each test can sign real RS256 JWTs (via `TestKeyPair::sign`)
    /// that the router's real `Verifier`/middleware will actually validate -
    /// no mock-authorities shortcut.
    async fn setup() -> (
        testcontainers::ContainerAsync<Postgres>,
        Router,
        PgPool,
        TestKeyPair,
    ) {
        // Module default is postgres:11-alpine, which predates the built-in
        // gen_random_uuid(); pin to 16-alpine to match the other services.
        let container = Postgres::default()
            .with_tag("16-alpine")
            .start()
            .await
            .unwrap();
        let port = container.get_host_port_ipv4(5432).await.unwrap();
        let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
        let pool = PgPool::connect(&url).await.unwrap();
        // The migration file has multiple statements; `sqlx::query` uses the
        // prepared-statement protocol which rejects those, so use `raw_sql`
        // (simple query protocol) here instead.
        sqlx::raw_sql(include_str!("../migrations/0001_init.sql"))
            .execute(&pool)
            .await
            .unwrap();
        let pool_for_test = pool.clone();
        let service = Arc::new(CardsGrpcService::new(pool, Repository));
        let keys = TestKeyPair::generate();
        let verifier = Arc::new(Verifier::from_key(keys.decoding_key.clone()));
        (container, router(service, verifier), pool_for_test, keys)
    }

    fn bearer(token: &str) -> String {
        format!("Bearer {token}")
    }

    #[tokio::test]
    async fn create_then_get_card_round_trips() {
        let (_container, app, _pool, keys) = setup().await;

        let create_body = r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#;
        let response = app
            .clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["banker"])))
                    .body(Body::from(create_body))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::CREATED);
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(created["accountId"], "11111111-1111-1111-1111-111111111111");
        assert_eq!(created["lastFour"].as_str().unwrap().len(), 4);
        assert_eq!(created["status"], "CARD_STATUS_ACTIVE");
        let id = created["id"].as_str().unwrap();

        // A plain `customer` token is sufficient for a read - exercises the
        // "any authenticated role" branch distinctly from the banker-only
        // create above.
        let get_response = app
            .oneshot(
                HttpRequest::get(format!("/cards/{id}"))
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(get_response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn get_card_with_unknown_id_returns_problem_details() {
        let (_container, app, _pool, keys) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::get("/cards/00000000-0000-0000-0000-000000000000")
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
        assert_eq!(
            response.headers().get("content-type").unwrap(),
            "application/problem+json"
        );
    }

    #[tokio::test]
    async fn list_cards_by_account_returns_created_card() {
        let (_container, app, _pool, keys) = setup().await;
        let account_id = "11111111-1111-1111-1111-111111111111";

        app.clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["banker"])))
                    .body(Body::from(format!(r#"{{"accountId":"{account_id}"}}"#)))
                    .unwrap(),
            )
            .await
            .unwrap();

        let response = app
            .oneshot(
                HttpRequest::get(format!("/cards?accountId={account_id}"))
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let cards: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(cards.as_array().unwrap().len(), 1);
    }

    #[tokio::test]
    async fn list_cards_without_account_id_returns_problem_details() {
        let (_container, app, _pool, keys) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::get("/cards")
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
        assert_eq!(
            response.headers().get("content-type").unwrap(),
            "application/problem+json"
        );
    }

    #[tokio::test]
    async fn freeze_then_unfreeze_round_trips() {
        let (_container, app, _pool, keys) = setup().await;

        let response = app
            .clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["banker"])))
                    .body(Body::from(
                        r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#,
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
        let id = created["id"].as_str().unwrap().to_string();

        // Freeze/unfreeze aren't role-restricted beyond "authenticated" -
        // use a `customer` token to prove that's genuinely enough.
        let freeze_response = app
            .clone()
            .oneshot(
                HttpRequest::post(format!("/cards/{id}/freeze"))
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(freeze_response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(freeze_response.into_body(), usize::MAX)
            .await
            .unwrap();
        let frozen: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(frozen["status"], "CARD_STATUS_FROZEN");

        let unfreeze_response = app
            .oneshot(
                HttpRequest::post(format!("/cards/{id}/unfreeze"))
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(unfreeze_response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(unfreeze_response.into_body(), usize::MAX)
            .await
            .unwrap();
        let unfrozen: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(unfrozen["status"], "CARD_STATUS_ACTIVE");
    }

    #[tokio::test]
    async fn freeze_closed_card_returns_422_problem_details() {
        use crate::domain::CardStatus;

        let (_container, app, pool, keys) = setup().await;

        let create_body = r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#;
        let response = app
            .clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["banker"])))
                    .body(Body::from(create_body))
                    .unwrap(),
            )
            .await
            .unwrap();
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
        let id = created["id"].as_str().unwrap().to_string();

        // Simulate a future close operation directly via the repository,
        // bypassing the (not-yet-implemented) close RPC/route.
        Repository
            .update_status(&pool, &id, CardStatus::Closed)
            .await
            .unwrap();

        let response = app
            .oneshot(
                HttpRequest::post(format!("/cards/{id}/freeze"))
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::UNPROCESSABLE_ENTITY);
        assert_eq!(
            response.headers().get("content-type").unwrap(),
            "application/problem+json"
        );
    }

    #[tokio::test]
    async fn create_card_with_malformed_json_returns_problem_details() {
        let (_container, app, _pool, keys) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["banker"])))
                    .body(Body::from("not json"))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
        assert_eq!(
            response.headers().get("content-type").unwrap(),
            "application/problem+json"
        );
    }

    #[tokio::test]
    async fn create_card_without_token_returns_401() {
        let (_container, app, _pool, _keys) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .body(Body::from(
                        r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#,
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn create_card_with_customer_role_returns_403() {
        let (_container, app, _pool, keys) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .header("authorization", bearer(&keys.sign(&["customer"])))
                    .body(Body::from(
                        r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#,
                    ))
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::FORBIDDEN);
    }
}
