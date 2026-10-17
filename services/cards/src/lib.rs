pub mod domain;
pub mod grpc;
pub mod repository;

pub mod proto {
    pub mod acmebank {
        pub mod cards {
            pub mod v1 {
                tonic::include_proto!("acmebank.cards.v1");
            }
        }
    }
}
