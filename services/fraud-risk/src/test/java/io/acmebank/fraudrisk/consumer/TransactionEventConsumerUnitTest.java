package io.acmebank.fraudrisk.consumer;

import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.transactions.v1.Transaction;
import io.acmebank.proto.transactions.v1.TransactionStatus;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Fast, no-Docker counterpart to {@link TransactionEventConsumerTest}: proves that
 * {@link TransactionEventConsumer} extracts and continues the {@code traceparent}
 * header from the incoming record, rather than exercising it through real Kafka.
 */
class TransactionEventConsumerUnitTest {

    private static final String KNOWN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String KNOWN_PARENT_SPAN_ID = "00f067aa0ba902b7";

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_continuesTraceFromIncomingTraceparentHeader() throws Exception {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        FlaggedTransactionRepository repository = mock(FlaggedTransactionRepository.class);
        KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
        TransactionEventConsumer consumer = new TransactionEventConsumer(repository, kafkaTemplate, openTelemetry);

        String transactionId = "11111111-1111-1111-1111-111111111111";
        Transaction tx = Transaction.newBuilder()
                .setId(transactionId)
                .setFromAccountId("33333333-3333-3333-3333-333333333333")
                .setToAccountId("44444444-4444-4444-4444-444444444444")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(5_000_000L).build())
                .setStatus(TransactionStatus.TRANSACTION_STATUS_COMPLETED)
                .build();

        RecordHeaders headers = new RecordHeaders();
        headers.add("event-type", "TransactionCompleted".getBytes(StandardCharsets.UTF_8));
        headers.add("traceparent", ("00-" + KNOWN_TRACE_ID + "-" + KNOWN_PARENT_SPAN_ID + "-01").getBytes(StandardCharsets.UTF_8));

        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                "transactions.events", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0,
                transactionId, tx.toByteArray(), headers, Optional.empty());

        consumer.onMessage(record);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        SpanData consumedSpan = spans.get(0);
        assertEquals("fraud.consume", consumedSpan.getName());
        assertEquals(KNOWN_TRACE_ID, consumedSpan.getTraceId(), "consumer span should be parented to the incoming traceparent's trace");

        ArgumentCaptor<Message<byte[]>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(messageCaptor.capture());
        String traceparent = (String) messageCaptor.getValue().getHeaders().get("traceparent");

        assertNotNull(traceparent, "republished fraud.events message should carry a traceparent header");
        assertTrue(traceparent.matches("00-" + KNOWN_TRACE_ID + "-[0-9a-f]{16}-[0-9a-f]{2}"),
                "republished traceparent should continue the same trace, got: " + traceparent);
    }
}
