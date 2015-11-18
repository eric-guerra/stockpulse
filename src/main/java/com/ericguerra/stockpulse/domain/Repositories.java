package com.ericguerra.stockpulse.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    public interface StoreRepository extends JpaRepository<Store, String> {
        List<Store> findByOnlineEnabledTrue();
    }

    public interface InventoryRepository extends JpaRepository<InventoryRecord, InventoryRecord.Key> {
        List<InventoryRecord> findBySku(String sku);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select i from InventoryRecord i where i.storeId = :store and i.sku = :sku")
        Optional<InventoryRecord> lock(@Param("store") String storeId, @Param("sku") String sku);
    }

    public interface ReservationRepository extends JpaRepository<Reservation, String> {
        Optional<Reservation> findByOrderRefAndSkuAndStoreId(String orderRef, String sku, String storeId);

        @Query("select r from Reservation r where r.status = com.ericguerra.stockpulse.domain.Reservation.Status.HELD and r.expiresAt <= :now order by r.expiresAt")
        List<Reservation> findExpired(@Param("now") Instant now, org.springframework.data.domain.Pageable page);
    }

    public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
        @Modifying
        @Query("delete from ProcessedEvent e where e.processedAt < :cutoff")
        int deleteOlderThan(@Param("cutoff") Instant cutoff);
    }
}
