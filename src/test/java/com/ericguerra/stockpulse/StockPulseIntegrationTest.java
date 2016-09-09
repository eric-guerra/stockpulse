package com.ericguerra.stockpulse;

import com.ericguerra.stockpulse.cache.InMemoryAvailabilityCache;
import com.ericguerra.stockpulse.domain.Availability;
import com.ericguerra.stockpulse.domain.InventoryRecord;
import com.ericguerra.stockpulse.domain.Repositories.InventoryRepository;
import com.ericguerra.stockpulse.domain.Repositories.ProcessedEventRepository;
import com.ericguerra.stockpulse.domain.Repositories.ReservationRepository;
import com.ericguerra.stockpulse.domain.Reservation;
import com.ericguerra.stockpulse.events.InventoryEvent;
import com.ericguerra.stockpulse.service.InventoryService;
import com.ericguerra.stockpulse.service.ReservationService;
import com.ericguerra.stockpulse.service.StoreFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.class)
class StockPulseIntegrationTest {
    @Autowired InventoryService inventory;
    @Autowired ReservationService reservations;
    @Autowired StoreFinder finder;
    @Autowired InventoryRepository inventoryRepo;
    @Autowired ReservationRepository reservationRepo;
    @Autowired ProcessedEventRepository processedRepo;
    @Autowired InMemoryAvailabilityCache cache;
    @Autowired TestSupport.RecordingPublisher publisher;
    @Autowired MockMvc mvc;

    private int seq;

    @BeforeEach
    void reset() {
        reservationRepo.deleteAll();
        inventoryRepo.deleteAll();
        processedRepo.deleteAll();
        cache.clear();
        publisher.published.clear();
        TestSupport.NOW.set(Instant.parse("2016-03-01T15:00:00Z"));
    }

    private InventoryEvent event(InventoryEvent.Type type, String store, String sku, int qty) {
        return new InventoryEvent("evt-" + (++seq), type, store, sku, qty, Instant.now());
    }

    private void stock(String store, String sku, int qty, int safety) {
        inventoryRepo.save(new InventoryRecord(store, sku, qty, safety));
    }

    @Test
    void eventsAreAppliedExactlyOnce() {
        stock("HOU01", "TENT-2P", 10, 0);
        InventoryEvent sale = event(InventoryEvent.Type.SALE, "HOU01", "TENT-2P", 3);
        assertEquals(InventoryService.Outcome.APPLIED, inventory.apply(sale));
        assertEquals(InventoryService.Outcome.DUPLICATE, inventory.apply(sale), "redelivery is ignored");
        assertEquals(7, inventoryRepo.findById(new InventoryRecord.Key("HOU01", "TENT-2P")).orElseThrow().getOnHand());

        inventory.apply(event(InventoryEvent.Type.COUNT, "HOU01", "TENT-2P", 5));
        assertEquals(5, inventoryRepo.findById(new InventoryRecord.Key("HOU01", "TENT-2P")).orElseThrow().getOnHand());
    }

    @Test
    void unknownSkuAtStoreCreatesRecordAndUnknownStoreIsRejected() {
        inventory.apply(event(InventoryEvent.Type.RECEIPT, "KTY01", "NEW-SKU", 12));
        assertEquals(12, inventory.availabilityBySku("NEW-SKU").get("KTY01"));
        assertThrows(IllegalArgumentException.class, () -> inventory.apply(event(InventoryEvent.Type.SALE, "NOPE", "X", 1)));
    }

    @Test
    void cacheIsUpdatedAfterCommitAndChangesArePublished() {
        stock("HOU01", "MUG", 5, 1);
        stock("HOU02", "MUG", 2, 0);
        assertEquals(Map.of("HOU01", 4, "HOU02", 2), inventory.availabilityBySku("MUG"));

        inventory.apply(event(InventoryEvent.Type.SALE, "HOU01", "MUG", 2));
        assertEquals(2, cache.get("MUG").orElseThrow().get("HOU01"), "write-through on commit");
        var change = publisher.published.get(0);
        assertEquals("HOU01", change.storeId());
        assertEquals(2, change.available());
        assertEquals(Availability.Level.LOW_STOCK, change.level());

        // A change that doesn't move sellable units (absorbed by safety stock) isn't published.
        stock("SUG01", "MUG", 1, 2);
        publisher.published.clear();
        inventory.apply(event(InventoryEvent.Type.RECEIPT, "SUG01", "MUG", 1));
        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void reservationLifecycle() {
        stock("HOU01", "SHOE-10", 4, 1); // 3 sellable
        Reservation r = reservations.reserve("HOU01", "SHOE-10", 2, "ORD-1");
        assertEquals(1, inventory.availabilityBySku("SHOE-10").get("HOU01"));
        assertSame(Reservation.Status.HELD, r.getStatus());
        assertEquals(r.getId(), reservations.reserve("HOU01", "SHOE-10", 2, "ORD-1").getId(), "idempotent retry");

        var e = assertThrows(ReservationService.InsufficientStockException.class, () -> reservations.reserve("HOU01", "SHOE-10", 2, "ORD-2"));
        assertEquals(1, e.available());

        reservations.confirm(r.getId());
        var rec = inventoryRepo.findById(new InventoryRecord.Key("HOU01", "SHOE-10")).orElseThrow();
        assertEquals(2, rec.getOnHand(), "pickup decrements on-hand");
        assertEquals(0, rec.getReserved());
        assertThrows(ReservationService.ReservationStateException.class, () -> reservations.release(r.getId()));
    }

    @Test
    void expiredHoldsReturnStock() {
        stock("HOU02", "PACK", 3, 0);
        Reservation r = reservations.reserve("HOU02", "PACK", 3, "ORD-9");
        assertEquals(0, inventory.availabilityBySku("PACK").get("HOU02"));

        TestSupport.NOW.set(TestSupport.NOW.get().plus(Duration.ofHours(3)));
        assertThrows(ReservationService.ReservationStateException.class, () -> reservations.confirm(r.getId()), "too late to confirm");
        assertEquals(1, reservations.expireHolds());
        assertEquals(3, inventory.availabilityBySku("PACK").get("HOU02"));
        assertEquals(Reservation.Status.EXPIRED, reservationRepo.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test
    void storeFinderSortsByDistanceAndFiltersStock() {
        finder.refreshStores();
        stock("HOU01", "TENT", 6, 0);
        stock("KTY01", "TENT", 0, 0);
        stock("AUS01", "TENT", 9, 0);
        // Shopper downtown Houston, 30-mile radius: Austin is out of range.
        var results = finder.near("TENT", 29.7604, -95.3698, 30, false, 10);
        assertEquals("HOU02", results.get(0).storeId(), "Heights is closest to downtown");
        assertTrue(results.stream().noneMatch(s -> s.storeId().equals("AUS01")));
        var inStock = finder.near("TENT", 29.7604, -95.3698, 30, true, 10);
        assertEquals(1, inStock.size());
        assertEquals("HOU01", inStock.get(0).storeId());
    }

    @Test
    void httpApi() throws Exception {
        stock("HOU01", "LAMP", 2, 0);
        mvc.perform(get("/api/availability/LAMP/stores").param("lat", "29.74").param("lng", "-95.46").param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storeId").value("HOU01"))
                .andExpect(jsonPath("$[0].level").value("LOW_STOCK"));
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"HOU01\",\"sku\":\"LAMP\",\"qty\":5,\"orderRef\":\"WEB-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.available").value(2));
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"pos-1\",\"type\":\"SALE\",\"storeId\":\"HOU01\",\"sku\":\"LAMP\",\"quantity\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/availability/LAMP"))
                .andExpect(jsonPath("$.totalAvailable").value(2));
    }
}
