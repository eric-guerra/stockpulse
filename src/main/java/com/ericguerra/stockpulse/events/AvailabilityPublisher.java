package com.ericguerra.stockpulse.events;

import com.ericguerra.stockpulse.domain.Availability;

/** Announces sellable-quantity changes (e.g. so the website can flip "Pick up today" badges). */
public interface AvailabilityPublisher {
    record AvailabilityChanged(String sku, String storeId, int available, Availability.Level level) {}

    void publish(AvailabilityChanged change);
}
