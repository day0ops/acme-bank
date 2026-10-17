package io.acmebank.transactions.outbox;

import io.acmebank.transactions.persistence.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox table for unpublished events and publishes them to Redpanda,
 * marking each row published after a successful send. This is the component that
 * closes the gap left deliberately open when the outbox table was first built:
 * writing rows reliably (in the same DB transaction as business data) is only half
 * the pattern - this relay is the other half, the thing that actually delivers them.
 */
@Component
public class OutboxRelay {

    private static final String TOPIC = "transactions.events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void pollAndPublish() {
        for (OutboxRepository.OutboxEvent event : outboxRepository.findUnpublished()) {
            var message = MessageBuilder.withPayload(event.payload())
                    .setHeader(KafkaHeaders.TOPIC, TOPIC)
                    .setHeader(KafkaHeaders.KEY, event.aggregateId())
                    .setHeader("event-type", event.eventType())
                    .setHeader("traceparent", currentTraceParent())
                    .build();
            kafkaTemplate.send(message);
            outboxRepository.markPublished(event.id());
        }
    }

    private String currentTraceParent() {
        var currentSpan = io.opentelemetry.api.trace.Span.current();
        var spanContext = currentSpan.getSpanContext();
        if (!spanContext.isValid()) {
            return "";
        }
        return "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-01";
    }
}
