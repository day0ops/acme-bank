fn main() -> Result<(), Box<dyn std::error::Error>> {
    prost_build::compile_protos(
        &[
            "../../proto/acmebank/transactions/v1/transactions.proto",
            "../../proto/acmebank/fraud/v1/fraud.proto",
        ],
        &["../../proto"],
    )?;
    Ok(())
}
