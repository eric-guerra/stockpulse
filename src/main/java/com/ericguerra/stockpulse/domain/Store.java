package com.ericguerra.stockpulse.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "stores")
public class Store {
    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    /** Stores can be temporarily excluded from online availability (e.g. inventory count in progress). */
    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled = true;

    protected Store() {}

    public Store(String id, String name, double lat, double lng) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public boolean isOnlineEnabled() { return onlineEnabled; }
    public void setOnlineEnabled(boolean v) { this.onlineEnabled = v; }
}
