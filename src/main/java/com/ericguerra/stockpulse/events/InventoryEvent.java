package com.ericguerra.stockpulse.events;

import java.time.Instant;

/**
 * Inbound inventory movement from stores and the DC.
 * SALE / RETURN / RECEIPT / SHRINK carry a quantity delta; COUNT carries an absolute on-hand figure.
 */
public record InventoryEvent(String eventId, Type type, String storeId, String sku, int quantity, Instant occurredAt) {
    public enum Type { SALE, RETURN, RECEIPT, SHRINK, COUNT }

    public InventoryEvent {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (storeId == null || sku == null) throw new IllegalArgumentException("storeId and sku are required");
        if (type == Type.COUNT ? quantity < 0 : quantity <= 0) throw new IllegalArgumentException("invalid quantity " + quantity + " for " + type);
    }

    /** Signed change to on-hand for delta events. */
    public int delta() {
        return switch (type) {
            case SALE, SHRINK -> -quantity;
            case RETURN, RECEIPT -> quantity;
            case COUNT -> throw new IllegalStateException("COUNT is absolute");
        };
    }
}
