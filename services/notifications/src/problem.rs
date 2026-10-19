//! RFC 9457 Problem Details responses, shared by `rest` (REST handler errors)
//! and `auth` (authentication/authorization rejections).

use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Serialize;

#[derive(Serialize)]
pub(crate) struct Problem {
    #[serde(rename = "type")]
    problem_type: String,
    title: String,
    status: u16,
    detail: String,
}

pub(crate) fn problem_response(status: StatusCode, title: &str, detail: String) -> Response {
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
