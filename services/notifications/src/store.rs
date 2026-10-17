use crate::domain::Notification;
use std::collections::VecDeque;
use std::sync::Mutex;

const MAX_NOTIFICATIONS: usize = 100;

/// An in-memory, bounded ring buffer of recent notifications. Not persisted -
/// restarting the service loses history, which is an acceptable simplification
/// for a demo notification feed (the source of truth is Redpanda, not this cache).
pub struct NotificationStore {
    notifications: Mutex<VecDeque<Notification>>,
}

impl Default for NotificationStore {
    fn default() -> Self {
        Self::new()
    }
}

impl NotificationStore {
    pub fn new() -> Self {
        Self {
            notifications: Mutex::new(VecDeque::with_capacity(MAX_NOTIFICATIONS)),
        }
    }

    pub fn push(&self, notification: Notification) {
        let mut guard = self.notifications.lock().unwrap();
        if guard.len() >= MAX_NOTIFICATIONS {
            guard.pop_front();
        }
        guard.push_back(notification);
    }

    pub fn recent(&self) -> Vec<Notification> {
        self.notifications.lock().unwrap().iter().cloned().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn push_then_recent_returns_pushed_notification() {
        let store = NotificationStore::new();
        store.push(Notification::new(
            "TransactionCompleted",
            "tx-1",
            "Transfer completed".to_string(),
        ));

        let recent = store.recent();

        assert_eq!(recent.len(), 1);
        assert_eq!(recent[0].transaction_id, "tx-1");
    }

    #[test]
    fn push_beyond_capacity_evicts_oldest() {
        let store = NotificationStore::new();
        for i in 0..150 {
            store.push(Notification::new(
                "TransactionCompleted",
                &format!("tx-{i}"),
                "msg".to_string(),
            ));
        }

        let recent = store.recent();

        assert_eq!(recent.len(), 100);
        assert_eq!(recent[0].transaction_id, "tx-50"); // the first 50 were evicted
        assert_eq!(recent[99].transaction_id, "tx-149");
    }
}
