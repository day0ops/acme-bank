use crate::auth::Verifier;
use crate::store::NotificationStore;
use axum::extract::ws::{Message, WebSocket};
use axum::extract::{Query, State, WebSocketUpgrade};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Clone)]
struct AppState {
    store: Arc<NotificationStore>,
    verifier: Arc<Verifier>,
}

/// Wires the Notifications REST/WebSocket API. `GET /notifications` requires
/// any authenticated caller (no specific role - every Acme Bank role is
/// entitled to see its own notification feed). `GET /ws` upgrades to a
/// WebSocket stream of new notifications, authenticated via a `token` query
/// parameter since browsers' native WebSocket API cannot set custom headers.
pub fn router(store: Arc<NotificationStore>, verifier: Arc<Verifier>) -> Router {
    let state = AppState { store, verifier };
    Router::new()
        .route("/notifications", get(list_notifications))
        .route("/ws", get(websocket_handler))
        .with_state(state)
}

async fn list_notifications(State(state): State<AppState>, headers: HeaderMap) -> Response {
    let token = match extract_bearer(&headers) {
        Some(t) => t,
        None => return StatusCode::UNAUTHORIZED.into_response(),
    };
    if state.verifier.validate(&token).is_err() {
        return StatusCode::UNAUTHORIZED.into_response();
    }
    Json(state.store.recent()).into_response()
}

fn extract_bearer(headers: &HeaderMap) -> Option<String> {
    headers
        .get("Authorization")?
        .to_str()
        .ok()?
        .strip_prefix("Bearer ")
        .map(str::to_string)
}

async fn websocket_handler(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
    Query(params): Query<HashMap<String, String>>,
) -> Response {
    let token = match params.get("token") {
        Some(t) => t.clone(),
        None => return StatusCode::UNAUTHORIZED.into_response(),
    };
    if state.verifier.validate(&token).is_err() {
        return StatusCode::UNAUTHORIZED.into_response();
    }
    ws.on_upgrade(move |socket| handle_socket(socket, state))
}

async fn handle_socket(mut socket: WebSocket, state: AppState) {
    // Send current history on connect, then poll for new entries. A
    // production implementation would use a broadcast channel fed directly
    // by the Kafka consumer; polling the store is a simpler, adequate
    // approximation for a demo's notification volume.
    let mut last_sent_count = 0;
    loop {
        let recent = state.store.recent();
        if recent.len() > last_sent_count {
            for notification in &recent[last_sent_count..] {
                let payload = serde_json::to_string(notification).unwrap_or_default();
                if socket.send(Message::Text(payload)).await.is_err() {
                    return;
                }
            }
            last_sent_count = recent.len();
        }
        tokio::time::sleep(std::time::Duration::from_secs(2)).await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::auth::test_support::TestKeyPair;
    use crate::domain::Notification;
    use axum::body::Body;
    use axum::http::{Request as HttpRequest, StatusCode};
    use tower::ServiceExt;

    #[tokio::test]
    async fn get_notifications_with_valid_token_returns_recent_list() {
        let keys = TestKeyPair::generate();
        let token = keys.sign(&["customer"]);
        let verifier = Arc::new(Verifier::from_key(keys.decoding_key));
        let store = Arc::new(NotificationStore::new());
        store.push(Notification::new(
            "TransactionUpdate",
            "tx-1",
            "Transfer completed".to_string(),
        ));
        let app = router(store, verifier);

        let response = app
            .oneshot(
                HttpRequest::get("/notifications")
                    .header("Authorization", format!("Bearer {token}"))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        let notifications: Vec<serde_json::Value> = serde_json::from_slice(&body).unwrap();
        assert_eq!(notifications.len(), 1);
        assert_eq!(notifications[0]["transactionId"], "tx-1");
    }

    #[tokio::test]
    async fn get_notifications_without_token_returns_401() {
        let keys = TestKeyPair::generate();
        let verifier = Arc::new(Verifier::from_key(keys.decoding_key));
        let store = Arc::new(NotificationStore::new());
        let app = router(store, verifier);

        let response = app
            .oneshot(
                HttpRequest::get("/notifications")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }
}
