fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure()
        // The REST layer (src/rest.rs) serializes `Card` directly via `json!()`.
        .type_attribute("acmebank.cards.v1.Card", "#[derive(serde::Serialize)]")
        .compile_protos(
            &["../../proto/acmebank/cards/v1/cards.proto"],
            &["../../proto"],
        )?;
    Ok(())
}
