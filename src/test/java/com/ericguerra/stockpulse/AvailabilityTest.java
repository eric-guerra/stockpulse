package com.ericguerra.stockpulse;

import com.ericguerra.stockpulse.domain.Availability;
import com.ericguerra.stockpulse.events.InventoryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityTest {
    @Test
    void sellableSubtractsReservedAndSafetyStockButNeverGoesNegative() {
        assertEquals(6, Availability.sellable(10, 2, 2));
        assertEquals(0, Availability.sellable(3, 2, 2));
        assertEquals(0, Availability.sellable(-4, 0, 0), "on-hand can briefly be negative");
    }

    @Test
    void levels() {
        assertEquals(Availability.Level.OUT_OF_STOCK, Availability.level(0));
        assertEquals(Availability.Level.LOW_STOCK, Availability.level(3));
        assertEquals(Availability.Level.IN_STOCK, Availability.level(4));
    }

    @Test
    void distanceInMiles() {
        // Galleria to Katy is roughly 22 miles.
        double miles = Availability.milesBetween(29.7390, -95.4637, 29.7858, -95.8245);
        assertTrue(miles > 20 && miles < 24, String.valueOf(miles));
    }

    @Test
    void eventValidationAndDeltas() {
        Instant t = Instant.now();
        assertEquals(-2, new InventoryEvent("e1", InventoryEvent.Type.SALE, "HOU01", "SKU", 2, t).delta());
        assertEquals(5, new InventoryEvent("e2", InventoryEvent.Type.RECEIPT, "HOU01", "SKU", 5, t).delta());
        assertDoesNotThrow(() -> new InventoryEvent("e3", InventoryEvent.Type.COUNT, "HOU01", "SKU", 0, t));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEvent("e4", InventoryEvent.Type.SALE, "HOU01", "SKU", 0, t));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEvent("", InventoryEvent.Type.SALE, "HOU01", "SKU", 1, t));
        assertThrows(IllegalStateException.class, () -> new InventoryEvent("e5", InventoryEvent.Type.COUNT, "HOU01", "SKU", 3, t).delta());
    }
}
