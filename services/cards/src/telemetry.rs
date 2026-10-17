use opentelemetry::trace::TracerProvider as _;
use opentelemetry::KeyValue;
use opentelemetry_otlp::WithExportConfig;
use opentelemetry_sdk::trace::Config;
use opentelemetry_sdk::Resource;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::util::SubscriberInitExt;

/// Initializes the global tracing subscriber with an OTLP/gRPC exporter,
/// defaulting to a local collector when `OTEL_EXPORTER_OTLP_ENDPOINT` is unset.
///
/// Spans are batch-exported on the Tokio runtime and tagged with a
/// `service.name` of "cards" so the collector can attribute them correctly.
pub fn init_tracing() {
    let endpoint = std::env::var("OTEL_EXPORTER_OTLP_ENDPOINT")
        .unwrap_or_else(|_| "http://localhost:4317".to_string());

    let tracer_provider = opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_exporter(
            opentelemetry_otlp::new_exporter()
                .tonic()
                .with_endpoint(endpoint),
        )
        .with_trace_config(
            Config::default()
                .with_resource(Resource::new(vec![KeyValue::new("service.name", "cards")])),
        )
        .install_batch(opentelemetry_sdk::runtime::Tokio)
        .expect("failed to install OTLP tracer");

    let tracer = tracer_provider.tracer("cards");
    let telemetry_layer = tracing_opentelemetry::layer().with_tracer(tracer);

    tracing_subscriber::registry()
        .with(telemetry_layer)
        .with(tracing_subscriber::fmt::layer())
        .init();
}
