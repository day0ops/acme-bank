package io.acmebank.transactions.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Appends domain events to the outbox table, to be published by a relay (added in a later task). */
@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts an unpublished event row, intended to run inside the same transaction as the business data change it describes. */
    public void append(String aggregateType, String aggregateId, String eventType, String jsonPayload) {
        jdbcTemplate.update(
                "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload) VALUES (?, ?::uuid, ?, ?::jsonb)",
                aggregateType, aggregateId, eventType, jsonPayload);
    }
}
