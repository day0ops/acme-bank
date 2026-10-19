pub mod auth;
pub mod domain;
pub mod grpc;
pub mod problem;
pub mod repository;
pub mod rest;
pub mod telemetry;

pub mod proto {
    pub mod acmebank {
        pub mod cards {
            pub mod v1 {
                tonic::include_proto!("acmebank.cards.v1");
            }
        }
    }
}
