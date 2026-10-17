use crate::domain::{Card, CardStatus};
use sqlx::{PgPool, Row};

/// PostgreSQL-backed persistence for `Card` aggregates.
pub struct Repository;

impl Repository {
    /// Inserts `card` as a new row and returns it with its generated `id`.
    pub async fn create(&self, pool: &PgPool, card: &Card) -> Result<Card, sqlx::Error> {
        let row = sqlx::query(
            "INSERT INTO cards (account_id, last_four, status) VALUES ($1::uuid, $2, $3) RETURNING id, account_id, last_four, status",
        )
        .bind(&card.account_id)
        .bind(&card.last_four)
        .bind(status_to_i16(&card.status))
        .fetch_one(pool)
        .await?;
        Ok(row_to_card(&row))
    }

    /// Fetches the card with the given `id`. Returns `Ok(None)` if no such card exists.
    pub async fn get(&self, pool: &PgPool, id: &str) -> Result<Option<Card>, sqlx::Error> {
        let row =
            sqlx::query("SELECT id, account_id, last_four, status FROM cards WHERE id = $1::uuid")
                .bind(id)
                .fetch_optional(pool)
                .await?;
        Ok(row.map(|r| row_to_card(&r)))
    }

    /// Lists all cards for `account_id`, ordered by creation time.
    pub async fn list_by_account(
        &self,
        pool: &PgPool,
        account_id: &str,
    ) -> Result<Vec<Card>, sqlx::Error> {
        let rows = sqlx::query(
            "SELECT id, account_id, last_four, status FROM cards WHERE account_id = $1::uuid ORDER BY created_at",
        )
        .bind(account_id)
        .fetch_all(pool)
        .await?;
        Ok(rows.iter().map(row_to_card).collect())
    }

    /// Updates the status of the card with the given `id`.
    /// Returns `Ok(None)` if no card with `id` exists, rather than an error,
    /// so callers can map "not found" to a transport-layer NOT_FOUND response.
    pub async fn update_status(
        &self,
        pool: &PgPool,
        id: &str,
        status: CardStatus,
    ) -> Result<Option<Card>, sqlx::Error> {
        let row = sqlx::query(
            "UPDATE cards SET status = $1, updated_at = now() WHERE id = $2::uuid RETURNING id, account_id, last_four, status",
        )
        .bind(status_to_i16(&status))
        .bind(id)
        .fetch_optional(pool)
        .await?;
        Ok(row.map(|r| row_to_card(&r)))
    }
}

fn status_to_i16(status: &CardStatus) -> i16 {
    match status {
        CardStatus::Active => 1,
        CardStatus::Frozen => 2,
        CardStatus::Closed => 3,
    }
}

fn i16_to_status(value: i16) -> CardStatus {
    match value {
        2 => CardStatus::Frozen,
        3 => CardStatus::Closed,
        _ => CardStatus::Active,
    }
}

fn row_to_card(row: &sqlx::postgres::PgRow) -> Card {
    Card {
        id: row.get::<uuid::Uuid, _>("id").to_string(),
        account_id: row.get::<uuid::Uuid, _>("account_id").to_string(),
        last_four: row.get("last_four"),
        status: i16_to_status(row.get("status")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use testcontainers::{runners::AsyncRunner, ImageExt};
    use testcontainers_modules::postgres::Postgres;

    async fn setup() -> (testcontainers::ContainerAsync<Postgres>, PgPool) {
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
        (container, pool)
    }

    #[tokio::test]
    async fn create_then_get_round_trips() {
        let (_container, pool) = setup().await;
        let repo = Repository;

        let card = Card::issue(
            "11111111-1111-1111-1111-111111111111".to_string(),
            "4242".to_string(),
        )
        .unwrap();
        let created = repo.create(&pool, &card).await.unwrap();
        assert!(!created.id.is_empty());

        let fetched = repo.get(&pool, &created.id).await.unwrap();
        assert_eq!(fetched.unwrap().last_four, "4242");
    }

    #[tokio::test]
    async fn get_with_unknown_id_returns_none() {
        let (_container, pool) = setup().await;
        let repo = Repository;

        let result = repo
            .get(&pool, "00000000-0000-0000-0000-000000000000")
            .await
            .unwrap();
        assert!(result.is_none());
    }

    #[tokio::test]
    async fn update_status_persists_change() {
        let (_container, pool) = setup().await;
        let repo = Repository;

        let card = Card::issue(
            "11111111-1111-1111-1111-111111111111".to_string(),
            "4242".to_string(),
        )
        .unwrap();
        let created = repo.create(&pool, &card).await.unwrap();

        let updated = repo
            .update_status(&pool, &created.id, CardStatus::Frozen)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(updated.status, CardStatus::Frozen);

        let fetched = repo.get(&pool, &created.id).await.unwrap().unwrap();
        assert_eq!(fetched.status, CardStatus::Frozen);
    }
}
