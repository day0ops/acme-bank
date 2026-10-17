use crate::grpc::CardsGrpcService;
use crate::proto::acmebank::cards::v1::cards_service_server::CardsService as _;
use crate::proto::acmebank::cards::v1::{
    FreezeCardRequest, GetCardRequest, IssueCardRequest, ListCardsByAccountRequest,
    UnfreezeCardRequest,
};
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::collections::HashMap;
use std::sync::Arc;
use tonic::{Code, Request};

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

/// Wires the Cards REST API onto `service`, the same `CardsGrpcService`
/// implementation the gRPC transport uses.
pub fn router(service: Arc<CardsGrpcService>) -> Router {
    Router::new()
        .route("/cards", post(create_card).get(list_cards))
        .route("/cards/:id", get(get_card))
        .route("/cards/:id/freeze", post(freeze_card))
        .route("/cards/:id/unfreeze", post(unfreeze_card))
        .with_state(service)
}

async fn create_card(
    State(service): State<Arc<CardsGrpcService>>,
    Json(body): Json<IssueCardBody>,
) -> Response {
    match service
        .issue_card(Request::new(IssueCardRequest {
            account_id: body.account_id,
        }))
        .await
    {
        Ok(resp) => (StatusCode::CREATED, Json(json!(resp.into_inner().card))).into_response(),
        Err(status) => status_to_problem(status),
    }
}

async fn get_card(
    State(service): State<Arc<CardsGrpcService>>,
    Path(id): Path<String>,
) -> Response {
    match service.get_card(Request::new(GetCardRequest { id })).await {
        Ok(resp) => Json(json!(resp.into_inner().card)).into_response(),
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
        Ok(resp) => Json(json!(resp.into_inner().cards)).into_response(),
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
        Ok(resp) => Json(json!(resp.into_inner().card)).into_response(),
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
        Ok(resp) => Json(json!(resp.into_inner().card)).into_response(),
        Err(status) => status_to_problem(status),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::repository::Repository;
    use axum::body::Body;
    use axum::http::{Request as HttpRequest, StatusCode};
    use sqlx::PgPool;
    use testcontainers::{runners::AsyncRunner, ImageExt};
    use testcontainers_modules::postgres::Postgres;
    use tower::ServiceExt;

    async fn setup() -> (testcontainers::ContainerAsync<Postgres>, Router, PgPool) {
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
        (container, router(service), pool_for_test)
    }

    #[tokio::test]
    async fn create_then_get_card_round_trips() {
        let (_container, app, _pool) = setup().await;

        let create_body = r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#;
        let response = app
            .clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
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
        let id = created["id"].as_str().unwrap();

        let get_response = app
            .oneshot(
                HttpRequest::get(format!("/cards/{id}"))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(get_response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn get_card_with_unknown_id_returns_problem_details() {
        let (_container, app, _pool) = setup().await;

        let response = app
            .oneshot(
                HttpRequest::get("/cards/00000000-0000-0000-0000-000000000000")
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
        let (_container, app, _pool) = setup().await;
        let account_id = "11111111-1111-1111-1111-111111111111";

        app.clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
                    .body(Body::from(format!(r#"{{"accountId":"{account_id}"}}"#)))
                    .unwrap(),
            )
            .await
            .unwrap();

        let response = app
            .oneshot(
                HttpRequest::get(format!("/cards?accountId={account_id}"))
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
        let (_container, app, _pool) = setup().await;

        let response = app
            .oneshot(HttpRequest::get("/cards").body(Body::empty()).unwrap())
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
        let (_container, app, _pool) = setup().await;

        let response = app
            .clone()
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
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let created: serde_json::Value = serde_json::from_slice(&body).unwrap();
        let id = created["id"].as_str().unwrap().to_string();

        let freeze_response = app
            .clone()
            .oneshot(
                HttpRequest::post(format!("/cards/{id}/freeze"))
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
        assert_eq!(frozen["status"], 2);

        let unfreeze_response = app
            .oneshot(
                HttpRequest::post(format!("/cards/{id}/unfreeze"))
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
        assert_eq!(unfrozen["status"], 1);
    }

    #[tokio::test]
    async fn freeze_closed_card_returns_422_problem_details() {
        use crate::domain::CardStatus;

        let (_container, app, pool) = setup().await;

        let create_body = r#"{"accountId":"11111111-1111-1111-1111-111111111111"}"#;
        let response = app
            .clone()
            .oneshot(
                HttpRequest::post("/cards")
                    .header("content-type", "application/json")
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
}
