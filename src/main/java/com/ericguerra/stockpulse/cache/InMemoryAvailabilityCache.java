package com.ericguerra.stockpulse.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryAvailabilityCache implements AvailabilityCache {
    private final Map<String, Map<String, Integer>> data = new ConcurrentHashMap<>();

    @Override
    public Optional<Map<String, Integer>> get(String sku) {
        Map<String, Integer> m = data.get(sku);
        return m == null ? Optional.empty() : Optional.of(Map.copyOf(m));
    }

    @Override
    public void put(String sku, Map<String, Integer> byStore) {
        data.put(sku, new ConcurrentHashMap<>(byStore));
    }

    @Override
    public void updateIfPresent(String sku, String storeId, int available) {
        data.computeIfPresent(sku, (k, m) -> {
            Map<String, Integer> copy = new HashMap<>(m);
            copy.put(storeId, available);
            return new ConcurrentHashMap<>(copy);
        });
    }

    @Override
    public void evict(String sku) {
        data.remove(sku);
    }

    public void clear() {
        data.clear();
    }
}
