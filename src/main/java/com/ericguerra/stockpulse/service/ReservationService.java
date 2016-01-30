package com.ericguerra.stockpulse.service;

import com.ericguerra.stockpulse.domain.InventoryRecord;
import com.ericguerra.stockpulse.domain.Reservation;
import com.ericguerra.stockpulse.domain.Repositories.InventoryRepository;
import com.ericguerra.stockpulse.domain.Repositories.ReservationRepository;
import com.ericguerra.stockpulse.domain.Repositories.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Buy-online-pickup-in-store holds. Reserving locks the inventory row, checks sellable units and
 * increments `reserved` in one transaction. Confirming (order picked up) converts the hold into an
 * on-hand decrement; releasing or expiring gives the units back.
 */
@Service
public class ReservationService {
    public static class InsufficientStockException extends RuntimeException {
        private final int available;

        public InsufficientStockException(int available) {
            super("only " + available + " available");
            this.available = available;
        }

        public int available() { return available; }
    }

    public static class ReservationStateException extends RuntimeException {
        public ReservationStateException(String message) { super(message); }
    }

    private final InventoryRepository inventory;
    private final StoreRepository stores;
    private final ReservationRepository reservations;
    private final InventoryService inventoryService;
    private final Clock clock;
    private final Duration holdFor;

    public ReservationService(InventoryRepository inventory, StoreRepository stores, ReservationRepository reservations,
                              InventoryService inventoryService, Clock clock,
                              @Value("${stockpulse.hold-duration:PT2H}") Duration holdFor) {
        this.inventory = inventory;
        this.stores = stores;
        this.reservations = reservations;
        this.inventoryService = inventoryService;
        this.clock = clock;
        this.holdFor = holdFor;
    }

    /** Idempotent on (orderRef, sku, store): retrying a checkout returns the same hold. */
    @Transactional
    public Reservation reserve(String storeId, String sku, int qty, String orderRef) {
        if (qty <= 0 || qty > 20) throw new IllegalArgumentException("qty must be 1-20");
        var existing = reservations.findByOrderRefAndSkuAndStoreId(orderRef, sku, storeId);
        if (existing.isPresent()) return existing.get();

        var store = stores.findById(storeId).orElseThrow(() -> new IllegalArgumentException("unknown store " + storeId));
        if (!store.isOnlineEnabled()) throw new InsufficientStockException(0);
        InventoryRecord rec = inventory.lock(storeId, sku).orElseThrow(() -> new InsufficientStockException(0));
        int before = rec.available();
        if (before < qty) throw new InsufficientStockException(before);

        rec.reserve(qty);
        Instant now = clock.instant();
        Reservation r = reservations.save(new Reservation(storeId, sku, qty, orderRef, now, now.plus(holdFor)));
        inventoryService.afterCommit(rec, before);
        return r;
    }

    @Transactional
    public Reservation confirm(String id) {
        Reservation r = active(id);
        InventoryRecord rec = inventory.lock(r.getStoreId(), r.getSku()).orElseThrow();
        int before = rec.available();
        rec.release(r.getQty());
        rec.applyDelta(-r.getQty());
        r.setStatus(Reservation.Status.CONFIRMED);
        inventoryService.afterCommit(rec, before);
        return r;
    }

    @Transactional
    public Reservation release(String id) {
        Reservation r = active(id);
        giveBack(r, Reservation.Status.RELEASED);
        return r;
    }

    /** Sweeps expired holds in small batches. */
    @Scheduled(fixedDelayString = "${stockpulse.expiry-sweep-ms:30000}")
    @Transactional
    public int expireHolds() {
        List<Reservation> expired = reservations.findExpired(clock.instant(), PageRequest.of(0, 200));
        expired.forEach(r -> giveBack(r, Reservation.Status.EXPIRED));
        return expired.size();
    }

    private void giveBack(Reservation r, Reservation.Status status) {
        InventoryRecord rec = inventory.lock(r.getStoreId(), r.getSku()).orElseThrow();
        int before = rec.available();
        rec.release(r.getQty());
        r.setStatus(status);
        inventoryService.afterCommit(rec, before);
    }

    private Reservation active(String id) {
        Reservation r = reservations.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown reservation " + id));
        if (!r.isActive()) throw new ReservationStateException("reservation is " + r.getStatus());
        if (!r.getExpiresAt().isAfter(clock.instant())) throw new ReservationStateException("reservation has expired");
        return r;
    }

    public Map<String, Object> describe(Reservation r) {
        return Map.of("id", r.getId(), "storeId", r.getStoreId(), "sku", r.getSku(), "qty", r.getQty(),
                "status", r.getStatus(), "expiresAt", r.getExpiresAt().toString());
    }
}
