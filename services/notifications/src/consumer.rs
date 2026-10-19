use crate::domain::Notification;
use crate::proto::acmebank::fraud::v1::TransactionFlaggedEvent;
use crate::proto::acmebank::transactions::v1::{Transaction, TransactionStatus};
use crate::store::NotificationStore;
use opentelemetry::propagation::Extractor;
use opentelemetry::{global, Context};
use prost::Message;
use rdkafka::config::ClientConfig;
use rdkafka::consumer::{Consumer, StreamConsumer};
use rdkafka::message::{BorrowedHeaders, Headers};
use rdkafka::Message as _;
use std::sync::Arc;
use tracing_opentelemetry::OpenTelemetrySpanExt;

/// Consumes both transactions.events and fraud.events, converting each
/// message into a Notification and pushing it to the store. Runs forever;
/// intended to be spawned as a background task.
pub async fn run(store: Arc<NotificationStore>, brokers: &str) {
    let consumer: StreamConsumer = ClientConfig::new()
        .set("bootstrap.servers", brokers)
        .set("group.id", "notifications")
        .set("auto.offset.reset", "earliest")
        .create()
        .expect("failed to create Kafka consumer");

    consumer
        .subscribe(&["transactions.events", "fraud.events"])
        .expect("failed to subscribe to topics");

    loop {
        match consumer.recv().await {
            Ok(message) => {
                let span = tracing::info_span!("notifications.consume");
                span.set_parent(extract_parent_context(message.headers()));
                let _guard = span.enter();

                if let Some(payload) = message.payload() {
                    if let Some(notification) = decode_notification(message.topic(), payload) {
                        store.push(notification);
                    }
                }
            }
            Err(e) => {
                tracing::error!("Kafka consume error: {}", e);
            }
        }
    }
}

/// Extracts the W3C trace context from the `traceparent` header so this consumer's
/// span is parented to the producer's span instead of starting a disconnected trace.
fn extract_parent_context(headers: Option<&BorrowedHeaders>) -> Context {
    match headers {
        Some(headers) => {
            let extractor = HeaderExtractor(headers);
            global::get_text_map_propagator(|propagator| propagator.extract(&extractor))
        }
        None => Context::new(),
    }
}

struct HeaderExtractor<'a>(&'a BorrowedHeaders);

impl<'a> Extractor for HeaderExtractor<'a> {
    fn get(&self, key: &str) -> Option<&str> {
        self.0
            .iter()
            .find(|header| header.key == key)
            .and_then(|header| header.value)
            .and_then(|value| std::str::from_utf8(value).ok())
    }

    fn keys(&self) -> Vec<&str> {
        self.0.iter().map(|header| header.key).collect()
    }
}

fn decode_notification(topic: &str, payload: &[u8]) -> Option<Notification> {
    match topic {
        "transactions.events" => {
            let tx = Transaction::decode(payload).ok()?;
            // `status` is a raw i32 on the wire (proto3 enum encoding); convert it
            // back to the enum so the notification text reads e.g. "Completed"
            // rather than the numeric discriminant.
            let status =
                TransactionStatus::try_from(tx.status).unwrap_or(TransactionStatus::Unspecified);
            let message = format!("Transaction {} is now {:?}", tx.id, status);
            Some(Notification::new("TransactionUpdate", &tx.id, message))
        }
        "fraud.events" => {
            let event = TransactionFlaggedEvent::decode(payload).ok()?;
            let message = format!(
                "Transaction {} flagged: {}",
                event.transaction_id, event.reason
            );
            Some(Notification::new(
                "TransactionFlagged",
                &event.transaction_id,
                message,
            ))
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use opentelemetry::trace::TraceContextExt;
    use opentelemetry_sdk::propagation::TraceContextPropagator;
    use rdkafka::message::{Header, OwnedHeaders};

    #[test]
    fn extract_parent_context_continues_trace_from_traceparent_header() {
        global::set_text_map_propagator(TraceContextPropagator::new());

        let trace_id = "4bf92f3577b34da6a3ce929d0e0e4736";
        let span_id = "00f067aa0ba902b7";
        let traceparent = format!("00-{trace_id}-{span_id}-01");

        let headers = OwnedHeaders::new().insert(Header {
            key: "traceparent",
            value: Some(traceparent.as_str()),
        });

        let context = extract_parent_context(Some(headers.as_borrowed()));

        assert_eq!(
            context.span().span_context().trace_id().to_string(),
            trace_id
        );
    }

    #[test]
    fn extract_parent_context_without_headers_returns_empty_context() {
        let context = extract_parent_context(None);
        assert!(!context.has_active_span());
    }
}
