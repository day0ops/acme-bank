use crate::domain::{Card, CardError, CardStatus as DomainCardStatus};
use crate::proto::acmebank::cards::v1::cards_service_server::CardsService;
use crate::proto::acmebank::cards::v1::*;
use crate::repository::Repository;
use sqlx::PgPool;
use tonic::{Request, Response, Status};

pub struct CardsGrpcService {
    pool: PgPool,
    repository: Repository,
}

impl CardsGrpcService {
    /// Builds the gRPC service from a connection pool and repository.
    pub fn new(pool: PgPool, repository: Repository) -> Self {
        Self { pool, repository }
    }
}

/// Generates a random 4-digit string for display purposes. The service never
/// handles a full card number (PAN); only this last-four digest is stored.
fn generate_last_four() -> String {
    use rand::Rng;
    format!("{:04}", rand::thread_rng().gen_range(0..10000))
}

fn to_proto_status(status: &DomainCardStatus) -> i32 {
    match status {
        DomainCardStatus::Active => CardStatus::Active as i32,
        DomainCardStatus::Frozen => CardStatus::Frozen as i32,
        DomainCardStatus::Closed => CardStatus::Closed as i32,
    }
}

fn to_proto(card: &Card) -> crate::proto::acmebank::cards::v1::Card {
    crate::proto::acmebank::cards::v1::Card {
        id: card.id.clone(),
        account_id: card.account_id.clone(),
        last_four: card.last_four.clone(),
        status: to_proto_status(&card.status),
    }
}

#[tonic::async_trait]
impl CardsService for CardsGrpcService {
    async fn issue_card(
        &self,
        request: Request<IssueCardRequest>,
    ) -> Result<Response<IssueCardResponse>, Status> {
        let req = request.into_inner();
        let card = Card::issue(req.account_id, generate_last_four())
            .map_err(|e: CardError| Status::invalid_argument(e.to_string()))?;
        let created = self
            .repository
            .create(&self.pool, &card)
            .await
            .map_err(|e| Status::internal(e.to_string()))?;
        Ok(Response::new(IssueCardResponse {
            card: Some(to_proto(&created)),
        }))
    }

    async fn get_card(
        &self,
        request: Request<GetCardRequest>,
    ) -> Result<Response<GetCardResponse>, Status> {
        let req = request.into_inner();
        let card = self
            .repository
            .get(&self.pool, &req.id)
            .await
            .map_err(|e| Status::internal(e.to_string()))?
            .ok_or_else(|| Status::not_found(format!("card {} not found", req.id)))?;
        Ok(Response::new(GetCardResponse {
            card: Some(to_proto(&card)),
        }))
    }

    async fn list_cards_by_account(
        &self,
        request: Request<ListCardsByAccountRequest>,
    ) -> Result<Response<ListCardsByAccountResponse>, Status> {
        let req = request.into_inner();
        let cards = self
            .repository
            .list_by_account(&self.pool, &req.account_id)
            .await
            .map_err(|e| Status::internal(e.to_string()))?;
        Ok(Response::new(ListCardsByAccountResponse {
            cards: cards.iter().map(to_proto).collect(),
        }))
    }

    async fn freeze_card(
        &self,
        request: Request<FreezeCardRequest>,
    ) -> Result<Response<FreezeCardResponse>, Status> {
        let req = request.into_inner();
        let updated = self
            .repository
            .update_status(&self.pool, &req.id, DomainCardStatus::Frozen)
            .await
            .map_err(|e| Status::internal(e.to_string()))?
            .ok_or_else(|| Status::not_found(format!("card {} not found", req.id)))?;
        Ok(Response::new(FreezeCardResponse {
            card: Some(to_proto(&updated)),
        }))
    }

    async fn unfreeze_card(
        &self,
        request: Request<UnfreezeCardRequest>,
    ) -> Result<Response<UnfreezeCardResponse>, Status> {
        let req = request.into_inner();
        let updated = self
            .repository
            .update_status(&self.pool, &req.id, DomainCardStatus::Active)
            .await
            .map_err(|e| Status::internal(e.to_string()))?
            .ok_or_else(|| Status::not_found(format!("card {} not found", req.id)))?;
        Ok(Response::new(UnfreezeCardResponse {
            card: Some(to_proto(&updated)),
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use testcontainers::{runners::AsyncRunner, ImageExt};
    use testcontainers_modules::postgres::Postgres;

    async fn setup() -> (testcontainers::ContainerAsync<Postgres>, CardsGrpcService) {
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
        (container, CardsGrpcService::new(pool, Repository))
    }

    #[tokio::test]
    async fn issue_then_get_card_round_trips() {
        let (_container, service) = setup().await;

        let issued = service
            .issue_card(Request::new(IssueCardRequest {
                account_id: "11111111-1111-1111-1111-111111111111".to_string(),
            }))
            .await
            .unwrap()
            .into_inner();

        assert!(!issued.card.as_ref().unwrap().id.is_empty());

        let fetched = service
            .get_card(Request::new(GetCardRequest {
                id: issued.card.unwrap().id,
            }))
            .await
            .unwrap()
            .into_inner();

        assert_eq!(fetched.card.unwrap().last_four.len(), 4);
    }

    #[tokio::test]
    async fn get_card_with_unknown_id_returns_not_found() {
        let (_container, service) = setup().await;

        let result = service
            .get_card(Request::new(GetCardRequest {
                id: "00000000-0000-0000-0000-000000000000".to_string(),
            }))
            .await;

        assert_eq!(result.unwrap_err().code(), tonic::Code::NotFound);
    }

    #[tokio::test]
    async fn freeze_then_unfreeze_round_trips() {
        let (_container, service) = setup().await;

        let issued = service
            .issue_card(Request::new(IssueCardRequest {
                account_id: "11111111-1111-1111-1111-111111111111".to_string(),
            }))
            .await
            .unwrap()
            .into_inner();
        let id = issued.card.unwrap().id;

        let frozen = service
            .freeze_card(Request::new(FreezeCardRequest { id: id.clone() }))
            .await
            .unwrap()
            .into_inner();
        assert_eq!(frozen.card.unwrap().status, CardStatus::Frozen as i32);

        let unfrozen = service
            .unfreeze_card(Request::new(UnfreezeCardRequest { id }))
            .await
            .unwrap()
            .into_inner();
        assert_eq!(unfrozen.card.unwrap().status, CardStatus::Active as i32);
    }
}
