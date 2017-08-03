# StockPulse

Real-time, store-level inventory availability for omnichannel retail. It answers "is it in stock near me?" for product pages and holds stock for **buy online, pick up in store**. Built with **Spring Boot, MySQL, Redis and RabbitMQ**.

> Portfolio reference implementation of a 2015 retail-commerce project, built on Spring Boot 3 and Java 21.

```
POS / DC systems ──▶ RabbitMQ inventory.events (topic) ──▶ StockPulse listener ──▶ MySQL (source of truth)
                                                    │ invalid → DLQ             │ after commit
                                                    │                           ├──▶ Redis  avail:{sku} hash (write-through)
web / app ──▶ GET /api/availability/{sku}/stores ───┼── cache-aside, single-flight ◀─┘
          ──▶ POST /api/reservations (BOPIS)        └──▶ availability.changed (fanout) for downstream
```

## Design notes

- **Sellable = on hand − reserved − safety stock**, floored at 0, with a LOW_STOCK level at 3 or fewer ("Only 3 left"). On-hand may briefly go negative when a sale scans before its receipt; availability still floors at 0.
- **Exactly-once effects on an at-least-once bus**: each event id is recorded in `processed_events` in the same transaction as the stock change, so redeliveries become no-ops. A nightly job purges ids older than 7 days.
- **Row locking** (`SELECT … FOR UPDATE`) serializes concurrent sales and reservations for the same store and SKU.
- **Cache consistency**: Redis updates and bus publishes run in `afterCommit`, so they never reflect rolled-back data. Single-store updates use a Lua `HSET`-if-exists, so a partially cached SKU is never fabricated. A TTL bounds staleness, and cold reads are single-flighted per SKU so a viral product page can't stampede MySQL.
- **Reservations**: idempotent on `(orderRef, sku, store)`; confirm (picked up) converts the hold into an on-hand decrement; release or expiry (a sweeper every 30 s) gives the units back. Expired holds can't be confirmed.
- Invalid events go straight to the **DLQ**; transient failures are retried with backoff.

## API

| Method | Path | |
|---|---|---|
| GET | `/api/availability/{sku}/stores?lat&lng&radius=25&inStockOnly` | nearest stores with level |
| GET | `/api/availability/{sku}` | network total |
| POST | `/api/reservations` | `{storeId, sku, qty, orderRef}` → 201 / 409 `{available}` |
| POST | `/api/reservations/{id}/confirm` · `/release` | |
| POST | `/api/events` | HTTP fallback for inventory events |

## Run and test

```bash
docker compose up --build
mvn test     # H2 + in-memory cache; covers idempotency, after-commit cache/publish, BOPIS lifecycle and expiry, store finder, HTTP errors
```
