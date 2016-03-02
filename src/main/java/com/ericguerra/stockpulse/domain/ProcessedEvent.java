package com.ericguerra.stockpulse.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Idempotency ledger for inbound inventory events. RabbitMQ delivers at-least-once, so the same
 * POS sale may arrive twice; its id is recorded in the same transaction as the stock change.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
