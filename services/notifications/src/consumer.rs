use crate::domain::Notification;
use crate::proto::acmebank::fraud::v1::TransactionFlaggedEvent;
use crate::proto::acmebank::transactions::v1::{Transaction, TransactionStatus};
use crate::store::NotificationStore;
use prost::Message;
use rdkafka::config::ClientConfig;
use rdkafka::consumer::{Consumer, StreamConsumer};
use rdkafka::Message as _;
use std::sync::Arc;

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

fn decode_notification(topic: &str, payload: &[u8]) -> Option<Notification> {
    match topic {
        "transactions.events" => {
            let tx = Transaction::decode(payload).ok()?;
            // `status` is a raw i32 on the wire (proto3 enum encoding); convert it
            // back to the enum so the notification text reads e.g. "Completed"
            // rather than the numeric discriminant.
            let status = TransactionStatus::try_from(tx.status).unwrap_or(TransactionStatus::Unspecified);
            let message = format!("Transaction {} is now {:?}", tx.id, status);
            Some(Notification::new("TransactionUpdate", &tx.id, message))
        }
        "fraud.events" => {
            let event = TransactionFlaggedEvent::decode(payload).ok()?;
            let message = format!("Transaction {} flagged: {}", event.transaction_id, event.reason);
            Some(Notification::new("TransactionFlagged", &event.transaction_id, message))
        }
        _ => None,
    }
}
