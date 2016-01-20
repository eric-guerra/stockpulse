package com.ericguerra.stockpulse.service;

import com.ericguerra.stockpulse.cache.AvailabilityCache;
import com.ericguerra.stockpulse.domain.Availability;
import com.ericguerra.stockpulse.domain.InventoryRecord;
import com.ericguerra.stockpulse.domain.ProcessedEvent;
import com.ericguerra.stockpulse.domain.Repositories.InventoryRepository;
import com.ericguerra.stockpulse.domain.Repositories.ProcessedEventRepository;
import com.ericguerra.stockpulse.domain.Repositories.StoreRepository;
import com.ericguerra.stockpulse.events.AvailabilityPublisher;
import com.ericguerra.stockpulse.events.InventoryEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class InventoryService {
    public enum Outcome { APPLIED, DUPLICATE }

    private final InventoryRepository inventory;
    private final StoreRepository stores;
    private final ProcessedEventRepository processed;
    private final AvailabilityCache cache;
    private final AvailabilityPublisher publisher;
    private final Clock clock;
    /** Single-flight per SKU so a cold cache doesn't send 500 identical queries to MySQL. */
    private final Map<String, ReentrantLock> rebuildLocks = new ConcurrentHashMap<>();

    public InventoryService(InventoryRepository inventory, StoreRepository stores, ProcessedEventRepository processed,
                            AvailabilityCache cache, AvailabilityPublisher publisher, Clock clock) {
        this.inventory = inventory;
        this.stores = stores;
        this.processed = processed;
        this.cache = cache;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Applies an inbound event exactly once. */
    @Transactional
    public Outcome apply(InventoryEvent e) {
        if (processed.existsById(e.eventId())) return Outcome.DUPLICATE;
        if (!stores.existsById(e.storeId())) throw new IllegalArgumentException("unknown store " + e.storeId());

        InventoryRecord rec = inventory.lock(e.storeId(), e.sku())
                .orElseGet(() -> inventory.save(new InventoryRecord(e.storeId(), e.sku(), 0, 0)));
        int before = rec.available();
        if (e.type() == InventoryEvent.Type.COUNT) rec.setOnHand(e.quantity());
        else rec.applyDelta(e.delta());
        processed.save(new ProcessedEvent(e.eventId(), clock.instant()));
        afterCommit(rec, before);
        return Outcome.APPLIED;
    }

    /** Called by reservation flows after they change `reserved` on a locked record. */
    public void afterCommit(InventoryRecord rec, int availableBefore) {
        int after = rec.available();
        String sku = rec.getSku();
        String store = rec.getStoreId();
        Runnable propagate = () -> {
            cache.updateIfPresent(sku, store, after);
            if (after != availableBefore) {
                publisher.publish(new AvailabilityPublisher.AvailabilityChanged(sku, store, after, Availability.level(after)));
            }
        };
        // Only touch the cache and the bus once the database change is durable.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    propagate.run();
                }
            });
        } else {
            propagate.run();
        }
    }

    /** Redelivery windows are minutes, so a week of event ids is plenty for de-duplication. */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${stockpulse.dedup-purge-cron:0 15 3 * * *}")
    @Transactional
    public int purgeProcessedEvents() {
        return processed.deleteOlderThan(clock.instant().minus(java.time.Duration.ofDays(7)));
    }

    /** storeId -> sellable units for a SKU (cache-aside, with stampede protection). */
    public Map<String, Integer> availabilityBySku(String sku) {
        var cached = cache.get(sku);
        if (cached.isPresent()) return cached.get();
        ReentrantLock lock = rebuildLocks.computeIfAbsent(sku, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = cache.get(sku);
            if (cached.isPresent()) return cached.get();
            Map<String, Integer> fresh = new HashMap<>();
            for (InventoryRecord r : inventory.findBySku(sku)) fresh.put(r.getStoreId(), r.available());
            cache.put(sku, fresh);
            return fresh;
        } finally {
            lock.unlock();
            rebuildLocks.remove(sku, lock);
        }
    }
}
