package com.ericguerra.stockpulse.cache;

import java.util.Map;
import java.util.Optional;

/**
 * Per-SKU map of storeId -> sellable units. Product pages read this on every view, so it must
 * be O(1) and never hit the database on the hot path.
 */
public interface AvailabilityCache {
    Optional<Map<String, Integer>> get(String sku);

    void put(String sku, Map<String, Integer> byStore);

    /** Updates one store's entry only if the SKU is already cached (otherwise the next read rebuilds it). */
    void updateIfPresent(String sku, String storeId, int available);

    void evict(String sku);
}
