package com.ericguerra.stockpulse.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A buy-online-pickup-in-store hold. Expires automatically if the order isn't confirmed. */
@Entity
@Table(name = "reservations")
public class Reservation {
    public enum Status { HELD, CONFIRMED, RELEASED, EXPIRED }

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "store_id", nullable = false, length = 10)
    private String storeId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false)
    private int qty;

    @Column(name = "order_ref", nullable = false, length = 40)
    private String orderRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.HELD;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reservation() {}

    public Reservation(String storeId, String sku, int qty, String orderRef, Instant now, Instant expiresAt) {
        this.storeId = storeId;
        this.sku = sku;
        this.qty = qty;
        this.orderRef = orderRef;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return status == Status.HELD;
    }

    public void setStatus(Status status) { this.status = status; }
    public String getId() { return id; }
    public String getStoreId() { return storeId; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public String getOrderRef() { return orderRef; }
    public Status getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
