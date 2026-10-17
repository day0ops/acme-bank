package io.acmebank.customers.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    /**
     * Builds the SDK as a plain Spring bean rather than registering it as the JVM-wide
     * GlobalOpenTelemetry instance, since the latter can only be set once per JVM and
     * would throw on the second {@code @SpringBootTest} context created in the same test run.
     * Collaborators that need tracing (e.g. {@link io.acmebank.customers.grpc.GrpcServerLifecycle})
     * receive this bean via constructor injection instead of reading the global.
     */
    @Bean
    public OpenTelemetry openTelemetry(@Value("${otel.exporter.endpoint:localhost:4317}") String endpoint) {
        Resource resource = Resource.getDefault().toBuilder()
                .put(ResourceAttributes.SERVICE_NAME, "customers")
                .build();

        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://" + endpoint)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }
}
