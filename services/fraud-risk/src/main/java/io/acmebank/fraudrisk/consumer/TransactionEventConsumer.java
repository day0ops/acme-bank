package io.acmebank.fraudrisk.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import io.acmebank.fraudrisk.domain.FlaggedTransaction;
import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import io.acmebank.fraudrisk.rules.FraudRule;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.fraud.v1.TransactionFlaggedEvent;
import io.acmebank.proto.transactions.v1.Transaction;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@code transactions.events} and applies the fraud/risk rule set to
 * {@code TransactionCompleted} messages only; a failed transfer never moved money, so there is
 * nothing to flag. Transactions flagged by {@link FraudRule} are persisted and republished on
 * {@code fraud.events}.
 */
@Component
public class TransactionEventConsumer {

    private static final String FRAUD_EVENTS_TOPIC = "fraud.events";

    private static final TextMapGetter<Headers> HEADERS_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            List<String> keys = new ArrayList<>();
            headers.forEach(header -> keys.add(header.key()));
            return keys;
        }

        @Override
        public String get(Headers headers, String key) {
            if (headers == null) {
                return null;
            }
            Header header = headers.lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    };

    private final FlaggedTransactionRepository repository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TransactionEventConsumer(FlaggedTransactionRepository repository, KafkaTemplate<String, byte[]> kafkaTemplate, OpenTelemetry openTelemetry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("fraud-risk-consumer");
    }

    @KafkaListener(topics = "transactions.events", groupId = "fraud-risk")
    public void onMessage(ConsumerRecord<String, byte[]> record) throws InvalidProtocolBufferException {
        var eventTypeHeader = record.headers().lastHeader("event-type");
        String eventType = eventTypeHeader != null ? new String(eventTypeHeader.value()) : null;
        if (!"TransactionCompleted".equals(eventType)) {
            return;
        }

        Context parentContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), record.headers(), HEADERS_GETTER);
        Span span = tracer.spanBuilder("fraud.consume").setParent(parentContext).setSpanKind(SpanKind.CONSUMER).startSpan();
        try (Scope scope = span.makeCurrent()) {
            Transaction tx = Transaction.parseFrom(record.value());
            FraudRule.evaluate(tx.getAmount().getMinorUnits(), tx.getAmount().getCurrencyCode())
                    .ifPresent(reason -> {
                        FlaggedTransaction flagged = FlaggedTransaction.flag(
                                tx.getId(), reason, tx.getAmount().getMinorUnits(), tx.getAmount().getCurrencyCode());
                        repository.save(flagged);
                        publishFlagged(tx.getId(), reason, tx.getAmount(), span);
                    });
        } finally {
            span.end();
        }
    }

    private void publishFlagged(String transactionId, String reason, Money amount, Span parentSpan) {
        TransactionFlaggedEvent event = TransactionFlaggedEvent.newBuilder()
                .setTransactionId(transactionId)
                .setReason(reason)
                .setAmount(amount)
                .build();
        var message = MessageBuilder.withPayload(event.toByteArray())
                .setHeader(KafkaHeaders.TOPIC, FRAUD_EVENTS_TOPIC)
                .setHeader(KafkaHeaders.KEY, transactionId)
                .setHeader("traceparent", formatTraceParent(parentSpan))
                .build();
        kafkaTemplate.send(message);
    }

    private String formatTraceParent(Span span) {
        var ctx = span.getSpanContext();
        return "00-" + ctx.getTraceId() + "-" + ctx.getSpanId() + "-01";
    }
}
