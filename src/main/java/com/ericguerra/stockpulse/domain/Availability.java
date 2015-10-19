package com.ericguerra.stockpulse.domain;

/** Pure availability rules shared by the database path and the cache. */
public final class Availability {
    public enum Level { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    /** Shown as "Only N left" at or below this many units. */
    public static final int LOW_STOCK_THRESHOLD = 3;

    private Availability() {}

    public static int sellable(int onHand, int reserved, int safetyStock) {
        return Math.max(0, onHand - reserved - safetyStock);
    }

    public static Level level(int sellable) {
        if (sellable <= 0) return Level.OUT_OF_STOCK;
        if (sellable <= LOW_STOCK_THRESHOLD) return Level.LOW_STOCK;
        return Level.IN_STOCK;
    }

    /** Haversine distance in miles (store finder works in miles for US retail). */
    public static double milesBetween(double lat1, double lng1, double lat2, double lng2) {
        double r = 3958.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
