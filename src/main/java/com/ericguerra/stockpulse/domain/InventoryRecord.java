package com.ericguerra.stockpulse.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Stock of one SKU at one store. */
@Entity
@Table(name = "inventory")
@IdClass(InventoryRecord.Key.class)
public class InventoryRecord {
    public static class Key implements Serializable {
        private String storeId;
        private String sku;

        public Key() {}

        public Key(String storeId, String sku) {
            this.storeId = storeId;
            this.sku = sku;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(storeId, k.storeId) && Objects.equals(sku, k.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storeId, sku);
        }
    }

    @Id
    @Column(name = "store_id", length = 10)
    private String storeId;

    @Id
    @Column(length = 40)
    private String sku;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    /** Units held by unexpired buy-online-pickup-in-store reservations. */
    @Column(nullable = false)
    private int reserved;

    /** Buffer kept back from online availability to absorb shrink and count errors. */
    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected InventoryRecord() {}

    public InventoryRecord(String storeId, String sku, int onHand, int safetyStock) {
        this.storeId = storeId;
        this.sku = sku;
        this.onHand = onHand;
        this.safetyStock = safetyStock;
    }

    public int available() {
        return Availability.sellable(onHand, reserved, safetyStock);
    }

    public void applyDelta(int delta) {
        // On-hand can go negative when a sale scans before its receipt is recorded; it self-corrects.
        onHand += delta;
        updatedAt = Instant.now();
    }

    public void setOnHand(int count) {
        onHand = count;
        updatedAt = Instant.now();
    }

    public void reserve(int qty) {
        reserved += qty;
        updatedAt = Instant.now();
    }

    public void release(int qty) {
        reserved = Math.max(0, reserved - qty);
        updatedAt = Instant.now();
    }

    public String getStoreId() { return storeId; }
    public String getSku() { return sku; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public int getSafetyStock() { return safetyStock; }
    public void setSafetyStock(int s) { this.safetyStock = s; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
