package io.acmebank.transactions.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Appends domain events to the outbox table and exposes them to the relay that publishes them to Kafka. */
@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts an unpublished event row, intended to run inside the same transaction as the business data change it describes. */
    public void append(String aggregateType, String aggregateId, String eventType, byte[] payload) {
        jdbcTemplate.update(
                "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload) VALUES (?, ?::uuid, ?, ?)",
                aggregateType, aggregateId, eventType, payload);
    }

    /** An outbox row not yet delivered to Kafka. */
    public record OutboxEvent(String id, String aggregateId, String eventType, byte[] payload) {}

    /** Returns unpublished rows in insertion order, for the relay to deliver. */
    public List<OutboxEvent> findUnpublished() {
        return jdbcTemplate.query(
                "SELECT id, aggregate_id, event_type, payload FROM outbox_events WHERE published_at IS NULL ORDER BY created_at",
                (rs, rowNum) -> new OutboxEvent(
                        rs.getString("id"), rs.getString("aggregate_id"), rs.getString("event_type"), rs.getBytes("payload")));
    }

    /** Marks a row as delivered, so the relay doesn't republish it. */
    public void markPublished(String id) {
        jdbcTemplate.update("UPDATE outbox_events SET published_at = now() WHERE id = ?::uuid", id);
    }
}
