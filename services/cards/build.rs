fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure().compile_protos(
        &["../../proto/acmebank/cards/v1/cards.proto"],
        &["../../proto"],
    )?;
    Ok(())
}
