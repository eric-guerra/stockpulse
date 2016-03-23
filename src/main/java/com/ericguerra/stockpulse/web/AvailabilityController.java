package com.ericguerra.stockpulse.web;

import com.ericguerra.stockpulse.domain.Availability;
import com.ericguerra.stockpulse.events.InventoryEvent;
import com.ericguerra.stockpulse.service.InventoryService;
import com.ericguerra.stockpulse.service.ReservationService;
import com.ericguerra.stockpulse.service.StoreFinder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AvailabilityController {
    public record ReserveRequest(@NotBlank String storeId, @NotBlank String sku, @Min(1) @Max(20) int qty, @NotBlank String orderRef) {}

    private final StoreFinder finder;
    private final InventoryService inventory;
    private final ReservationService reservations;

    public AvailabilityController(StoreFinder finder, InventoryService inventory, ReservationService reservations) {
        this.finder = finder;
        this.inventory = inventory;
        this.reservations = reservations;
    }

    /** Product page widget: stores near the shopper and whether they can pick up today. */
    @GetMapping("/availability/{sku}/stores")
    public List<StoreFinder.StoreAvailability> stores(@PathVariable String sku, @RequestParam double lat, @RequestParam double lng,
                                                      @RequestParam(defaultValue = "25") double radius,
                                                      @RequestParam(defaultValue = "false") boolean inStockOnly,
                                                      @RequestParam(defaultValue = "10") int limit) {
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new IllegalArgumentException("invalid coordinates");
        return finder.near(sku, lat, lng, Math.min(radius, 100), inStockOnly, Math.min(Math.max(limit, 1), 50));
    }

    /** Network-wide summary for "ship to home" decisions. */
    @GetMapping("/availability/{sku}")
    public Map<String, Object> summary(@PathVariable String sku) {
        Map<String, Integer> byStore = inventory.availabilityBySku(sku);
        int total = byStore.values().stream().mapToInt(Integer::intValue).sum();
        long stocked = byStore.values().stream().filter(v -> v > 0).count();
        return Map.of("sku", sku, "totalAvailable", total, "storesWithStock", stocked, "level", Availability.level(total));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> reserve(@Valid @RequestBody ReserveRequest req) {
        return reservations.describe(reservations.reserve(req.storeId(), req.sku(), req.qty(), req.orderRef()));
    }

    @PostMapping("/reservations/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable String id) {
        return reservations.describe(reservations.confirm(id));
    }

    @PostMapping("/reservations/{id}/release")
    public Map<String, Object> release(@PathVariable String id) {
        return reservations.describe(reservations.release(id));
    }

    /** HTTP fallback for stores without a message-bus connection; same idempotent path as the listener. */
    @PostMapping("/events")
    public Map<String, Object> event(@RequestBody InventoryEvent event) {
        return Map.of("eventId", event.eventId(), "outcome", inventory.apply(event));
    }

    @ExceptionHandler(ReservationService.InsufficientStockException.class)
    ResponseEntity<Map<String, Object>> insufficient(ReservationService.InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "INSUFFICIENT_STOCK", "available", e.available()));
    }

    @ExceptionHandler(ReservationService.ReservationStateException.class)
    ResponseEntity<Map<String, Object>> state(ReservationService.ReservationStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "INVALID_STATE", "message", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, Object>> invalid(Exception e) {
        String msg = e.getCause() instanceof IllegalArgumentException iae ? iae.getMessage() : e.getMessage();
        return ResponseEntity.unprocessableEntity().body(Map.of("error", "INVALID", "message", String.valueOf(msg)));
    }
}
