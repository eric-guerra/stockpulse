package com.ericguerra.stockpulse.service;

import com.ericguerra.stockpulse.domain.Availability;
import com.ericguerra.stockpulse.domain.Repositories.StoreRepository;
import com.ericguerra.stockpulse.domain.Store;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** "Check store availability": nearest online-enabled stores with their stock level for a SKU. */
@Service
public class StoreFinder {
    public record StoreAvailability(String storeId, String name, double miles, int available, Availability.Level level) {}

    private final StoreRepository stores;
    private final InventoryService inventory;
    /** Store list changes rarely; keep it in memory and refresh on demand. */
    private final AtomicReference<List<Store>> storeCache = new AtomicReference<>();

    public StoreFinder(StoreRepository stores, InventoryService inventory) {
        this.stores = stores;
        this.inventory = inventory;
    }

    public void refreshStores() {
        storeCache.set(List.copyOf(stores.findByOnlineEnabledTrue()));
    }

    public List<StoreAvailability> near(String sku, double lat, double lng, double radiusMiles, boolean inStockOnly, int limit) {
        if (storeCache.get() == null) refreshStores();
        Map<String, Integer> bySku = inventory.availabilityBySku(sku);
        List<StoreAvailability> out = new ArrayList<>();
        for (Store s : storeCache.get()) {
            double miles = Availability.milesBetween(lat, lng, s.getLat(), s.getLng());
            if (miles > radiusMiles) continue;
            int available = bySku.getOrDefault(s.getId(), 0);
            if (inStockOnly && available <= 0) continue;
            out.add(new StoreAvailability(s.getId(), s.getName(), Math.round(miles * 10) / 10.0, available, Availability.level(available)));
        }
        out.sort(Comparator.comparingDouble(StoreAvailability::miles));
        return out.subList(0, Math.min(limit, out.size()));
    }
}
