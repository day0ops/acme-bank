#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Notification {
    pub id: String,
    pub event_type: String,
    pub transaction_id: String,
    pub message: String,
    pub timestamp: i64,
}

impl Notification {
    pub fn new(event_type: &str, transaction_id: &str, message: String) -> Self {
        Self {
            id: uuid::Uuid::new_v4().to_string(),
            event_type: event_type.to_string(),
            transaction_id: transaction_id.to_string(),
            message,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
        }
    }
}
