pub mod auth;
pub mod consumer;
pub mod domain;
pub mod problem;
pub mod rest;
pub mod store;
pub mod telemetry;

pub mod proto {
    pub mod acmebank {
        pub mod common {
            pub mod v1 {
                include!(concat!(env!("OUT_DIR"), "/acmebank.common.v1.rs"));
            }
        }
        pub mod transactions {
            pub mod v1 {
                include!(concat!(env!("OUT_DIR"), "/acmebank.transactions.v1.rs"));
            }
        }
        pub mod fraud {
            pub mod v1 {
                include!(concat!(env!("OUT_DIR"), "/acmebank.fraud.v1.rs"));
            }
        }
    }
}
