package io.acmebank.transactions.outbox;

import io.acmebank.transactions.persistence.OutboxRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table for unpublished events and publishes them to Redpanda,
 * marking each row published only after the broker has acknowledged receipt.
 * Each publish gets its own fresh span (not a continuation of the original
 * request's trace - that would require persisting the originating trace
 * context at outbox-write time, tracked as a follow-up), so every message
 * still carries a valid, non-empty W3C traceparent for downstream consumers.
 */
@Component
public class OutboxRelay {

    private static final String TOPIC = "transactions.events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Tracer tracer;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, byte[]> kafkaTemplate, OpenTelemetry openTelemetry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = openTelemetry.getTracer("outbox-relay");
    }

    @Scheduled(fixedDelay = 2000)
    public void pollAndPublish() {
        for (OutboxRepository.OutboxEvent event : outboxRepository.findUnpublished()) {
            Span span = tracer.spanBuilder("outbox.publish").setSpanKind(SpanKind.PRODUCER).startSpan();
            try (Scope scope = span.makeCurrent()) {
                var message = MessageBuilder.withPayload(event.payload())
                        .setHeader(KafkaHeaders.TOPIC, TOPIC)
                        .setHeader(KafkaHeaders.KEY, event.aggregateId())
                        .setHeader("event-type", event.eventType())
                        .setHeader("traceparent", formatTraceParent(span))
                        .build();
                kafkaTemplate.send(message).get(10, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.id());
            } catch (Exception e) {
                span.recordException(e);
                // Leave the row unpublished; the next poll retries it. This is the
                // at-least-once delivery guarantee the outbox pattern exists for.
            } finally {
                span.end();
            }
        }
    }

    private String formatTraceParent(Span span) {
        var ctx = span.getSpanContext();
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-01";
    }
}
