use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CardStatus {
    Active,
    Frozen,
    Closed,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CardError {
    InvalidLastFour,
    AlreadyClosed,
}

impl fmt::Display for CardError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CardError::InvalidLastFour => write!(f, "last_four must be exactly 4 digits"),
            CardError::AlreadyClosed => write!(f, "card is closed and cannot change status"),
        }
    }
}

impl std::error::Error for CardError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Card {
    pub id: String,
    pub account_id: String,
    pub last_four: String,
    pub status: CardStatus,
}

impl Card {
    /// Issues a new, unpersisted card (empty `id`) for the given account.
    /// `last_four` must be exactly 4 ASCII digits.
    pub fn issue(account_id: String, last_four: String) -> Result<Card, CardError> {
        if last_four.len() != 4 || !last_four.chars().all(|c| c.is_ascii_digit()) {
            return Err(CardError::InvalidLastFour);
        }
        Ok(Card {
            id: String::new(),
            account_id,
            last_four,
            status: CardStatus::Active,
        })
    }

    /// Transitions the card to `Frozen`. Fails if the card is already `Closed`.
    pub fn freeze(&mut self) -> Result<(), CardError> {
        if self.status == CardStatus::Closed {
            return Err(CardError::AlreadyClosed);
        }
        self.status = CardStatus::Frozen;
        Ok(())
    }

    /// Transitions the card to `Active`. Fails if the card is already `Closed`.
    pub fn unfreeze(&mut self) -> Result<(), CardError> {
        if self.status == CardStatus::Closed {
            return Err(CardError::AlreadyClosed);
        }
        self.status = CardStatus::Active;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn issue_with_valid_last_four_succeeds() {
        let card = Card::issue("acct-1".to_string(), "1234".to_string()).unwrap();
        assert_eq!(card.last_four, "1234");
        assert_eq!(card.status, CardStatus::Active);
    }

    #[test]
    fn issue_with_invalid_last_four_fails() {
        let result = Card::issue("acct-1".to_string(), "12a4".to_string());
        assert!(matches!(result, Err(CardError::InvalidLastFour)));
    }

    #[test]
    fn freeze_then_unfreeze_round_trips() {
        let mut card = Card::issue("acct-1".to_string(), "1234".to_string()).unwrap();
        card.freeze().unwrap();
        assert_eq!(card.status, CardStatus::Frozen);
        card.unfreeze().unwrap();
        assert_eq!(card.status, CardStatus::Active);
    }

    #[test]
    fn freeze_closed_card_fails() {
        let mut card = Card::issue("acct-1".to_string(), "1234".to_string()).unwrap();
        card.status = CardStatus::Closed;
        assert!(matches!(card.freeze(), Err(CardError::AlreadyClosed)));
    }
}
