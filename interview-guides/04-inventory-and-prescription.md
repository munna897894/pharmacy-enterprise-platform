# 04 — Inventory and Prescription (Prompt 04)

## Summary

`inventory-service` is a straightforward Spring MVC + JPA service for per-pharmacy stock records, stock adjustments, and low-stock visibility. `prescription-service` is another MVC + JPA service, but its current implementation is much simpler than the prompt docs imply: it manages a local prescription lifecycle (`PENDING -> ACTIVE -> FILLED/EXPIRED`) and never actually performs external verification or Kafka publication.

These two services are excellent interview-study material because they show both solid basics—optimistic locking, thin controllers, DTO/entity separation, RFC-7807 errors—and important architecture drift. The biggest real finding is that inventory/prescription event topics exist in docs and consumers, but these services currently publish nothing to them.

## Diagram — synchronous inventory check + local prescription workflow

```text
OrderService.createOrder()
   │
   ├─ for each item -> InventoryAvailabilityClient.checkAvailability(...)
   │                     │
   │                     └─ GET inventory-service
   │                        /api/v1/inventory/pharmacies/{pharmacyId}/products/{medicationId}
   │                              ▼
   │                           InventoryController
   │                              ▼
   │                           InventoryService
   │                              ▼
   │                           StockLevelRepository -> MySQL
   │
   └─ if unavailable -> reject order before save

Prescription flow
   Client -> PrescriptionController.createPrescription()
          -> PrescriptionService.createPrescription()
          -> Prescription + PrescriptionLine aggregate saved to MySQL
          -> later activate -> later fill line(s) -> status becomes FILLED when all lines are full

Kafka expectation in docs: inventory/prescription events
Reality in code: no producer in either service
```

## Key files

| File | What it shows |
|---|---|
| `services/inventory-service/src/main/java/com/jagapathi/pharmacy/inventory/api/controller/InventoryController.java` | Actual REST surface: stock-level lookup, pharmacy/product lookup, pharmacy inventory listing, low-stock listing, stock-level creation, stock adjustment, adjustment history, and `GET /api/v1/inventory/availability`. |
| `services/inventory-service/src/main/java/com/jagapathi/pharmacy/inventory/application/service/InventoryService.java` | Business rules: role checks in application layer, stock creation, add/remove logic, stock-adjustment audit records, and `getLowStockItems(...)` querying by `status = LOW_STOCK`. |
| `services/inventory-service/src/main/java/com/jagapathi/pharmacy/inventory/domain/model/StockLevel.java` | Inventory aggregate root: UUID-string ID, `pharmacyId`, `productId`, `quantityOnHand`, `reorderLevel`, `reorderQuantity`, string `status`, and `@Version` optimistic locking. `updateStatus()` derives `IN_STOCK`, `LOW_STOCK`, or `OUT_OF_STOCK`. |
| `services/inventory-service/src/main/resources/db/migration/V1__Initial_inventory_schema.sql` | Flyway schema: `stock_level` table with unique `(pharmacy_id, product_id)` and `stock_adjustment` child table for change history. |
| `services/inventory-service/src/main/java/com/jagapathi/pharmacy/inventory/infrastructure/config/SecurityConfig.java` | Downstream zero-trust validation intent: stateless resource server, method security, role mapping from `roles` claim, but a hard-coded JWKS URI of `http://localhost:8080/.well-known/jwks.json` that does not match the auth service’s real path. |
| `services/inventory-service/src/test/java/com/jagapathi/pharmacy/inventory/domain/StockLevelTest.java` | Quick proof of the domain rules: add stock keeps `IN_STOCK`, removing below reorder level yields `LOW_STOCK`, removing to zero yields `OUT_OF_STOCK`, and invalid/oversized decrements throw. |
| `services/prescription-service/src/main/java/com/jagapathi/pharmacy/prescription/api/controller/PrescriptionController.java` | Actual prescription endpoints: get by ID, list by status, list by customer, create, activate, and fill a line. There are **no** `/verify` or `/reject` endpoints. |
| `services/prescription-service/src/main/java/com/jagapathi/pharmacy/prescription/application/service/PrescriptionService.java` | True business behavior: UUID-format validation only for customer/prescriber, construction of `Prescription` + `PrescriptionLine`, activation, line filling, expiry sweep, and repository paging. The injected `RestTemplate` is never used. |
| `services/prescription-service/src/main/java/com/jagapathi/pharmacy/prescription/domain/model/Prescription.java` + `PrescriptionLine.java` | Prescription lifecycle and child-line behavior: status enum, optimistic locking on the aggregate, eager line loading, and `checkAndUpdateStatus()` that marks the prescription `FILLED` only when every line is fully filled. |
| `services/prescription-service/src/main/java/com/jagapathi/pharmacy/prescription/infrastructure/config/SecurityConfig.java` | JWT validation config plus another hard-coded JWKS URI: `http://auth-service:8080/api/v1/auth/.well-known/jwks.json`, which uses the right path but the wrong port because auth-service runs on `8081`. |
| `services/prescription-service/src/main/java/com/jagapathi/pharmacy/prescription/api/advice/GlobalExceptionHandler.java` | Centralized `ProblemDetail` mapping for not-found, invalid-state, and validation failures; good example of thin controllers plus a single error boundary. |
| `services/order-service/src/main/java/com/jagapathi/pharmacy/order/infrastructure/client/InventoryAvailabilityClient.java` | Cross-service proof that order creation is synchronous: it calls inventory over REST before saving the order, using `/api/v1/inventory/pharmacies/{pharmacyId}/products/{medicationId}` rather than Kafka reservation events. |
| `services/notification-service/src/main/resources/application.yml` + `services/order-service/src/main/java/com/jagapathi/pharmacy/order/infrastructure/kafka/OrderEventConsumer.java` | Evidence of the orphaned-topic gap: other services are wired to `pharmacy.prescription.events.v1` and `pharmacy.inventory.events.v1`, but inventory/prescription currently contain no producer code. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Optimistic locking (`@Version`) | `StockLevel` and `Prescription` both use `@Version`, so stale concurrent updates fail instead of silently overwriting each other. | This is the repo’s main concurrency-control pattern for mutable business state. |
| Unique pharmacy/product inventory identity | The DB and JPA model enforce one `stock_level` row per `(pharmacy_id, product_id)`. | Prevents duplicate stock records for the same item at the same store and simplifies availability lookups. |
| Threshold-derived stock status | `StockLevel.updateStatus()` computes `IN_STOCK`, `LOW_STOCK`, or `OUT_OF_STOCK` from `quantityOnHand` vs `reorderLevel`. | This is a common interview pattern: persist thresholds, derive operational state from them. |
| Adjustment-history audit trail | Every mutation through `adjustStock(...)` also inserts a `StockAdjustment` row with type, quantity, reason, and `adjustedBy`. | Shows how to preserve operational history without event sourcing the entire aggregate. |
| Thin-controller / service-layer business logic | Controllers mostly validate inputs, extract auth context, and delegate. Role-sensitive decisions still happen inside `InventoryService` and `PrescriptionService`. | Good layered architecture answer: HTTP concerns at the edge, business rules in application services. |
| Aggregate + child-entity model | `Prescription` owns a list of `PrescriptionLine` children and decides when the overall status becomes `FILLED`. | Interviewers often ask where state-transition logic should live; this is a clean aggregate-root example. |
| Partial-fill semantics | `PrescriptionLine.fill(...)` allows any positive dispensed amount up to the prescribed quantity; `isFullyFilled()` only becomes true when dispensed quantity is at least the full quantity. | Important nuance: a line can be partially dispensed while the prescription remains `ACTIVE`. |
| DTO/entity separation | Both services expose response records (`StockLevelResponse`, `PrescriptionResponse`) instead of returning JPA entities directly. | Prevents accidental persistence leakage and makes API evolution safer. |
| OAuth2 resource-server pattern downstream | Inventory and prescription both intend to re-validate JWTs themselves rather than trusting only the gateway. | This preserves defense in depth, although both services currently have JWKS URI drift that would hurt runtime auth. |
| Synchronous stock check vs async reservation | The real order flow checks stock synchronously over REST before order creation. There is no implemented async “reserve inventory” saga step inside `inventory-service`. | This matters because consistency, latency, and failure behavior differ a lot from the event-driven design in the docs. |
| Orphaned event topics | Docs and other services refer to `pharmacy.inventory.events.v1` and `pharmacy.prescription.events.v1`, but these services have no KafkaTemplate, StreamBridge, or `@KafkaListener` producer-side wiring. | This is a genuine architecture gap: consumers are waiting for events that never arrive. |

## Common interview Q&A

**Q: How does inventory prevent lost updates?**  
A: `StockLevel` uses `@Version`, so concurrent writers rely on optimistic locking. The service mutates the entity in memory and saves it; if another transaction updated the same row first, Hibernate should raise an optimistic-lock conflict instead of silently overwriting data.

**Q: Does the inventory service implement reservations?**  
A: No. The current code has stock-level creation, direct add/remove adjustments, low-stock queries, history, and a simple availability endpoint. There is no reservation table, no `reserved_quantity`, and no Kafka consumer/producer for order-driven reservations.

**Q: How is low stock determined in the actual code?**  
A: `StockLevel.updateStatus()` sets `LOW_STOCK` when `quantityOnHand <= reorderLevel`, `OUT_OF_STOCK` at zero, and `IN_STOCK` otherwise. `InventoryService.getLowStockItems(...)` simply queries for rows whose persisted status is `LOW_STOCK`.

**Q: How does order creation really check stock?**  
A: `OrderService.createOrder()` loops through requested items and calls `InventoryAvailabilityClient.checkAvailability(...)` synchronously for each one before the order is saved. That is an HTTP dependency, not an asynchronous inventory saga step.

**Q: What is the prescription state machine today?**  
A: `PrescriptionStatus` only contains `PENDING`, `ACTIVE`, `FILLED`, and `EXPIRED`. Create starts at `PENDING`, `activate()` moves to `ACTIVE`, `fill(...)` on lines can eventually make the aggregate `FILLED`, and `expirePrescriptions()` moves expired active prescriptions to `EXPIRED`.

**Q: Is external prescription verification implemented?**  
A: No. `PrescriptionService` has a `RestTemplate` dependency and the config includes base URLs for customer/inventory/product services, but the current validation methods only check whether IDs are valid UUID strings. No outbound HTTP verification call is made.

**Q: Why keep line-level state separate from prescription-level state?**  
A: Because a prescription can contain multiple medications with different fill progress. `Prescription.checkAndUpdateStatus()` aggregates child-line completion instead of flattening everything into one row.

**Q: Why use `BigDecimal` for quantities?**  
A: Both services model counts and dispensed quantities with `BigDecimal`, matching the repo’s general “never use double for business values” rule and allowing fractional quantities where the domain wants them.

**Q: What is the real event-driven story for these services?**  
A: Mostly aspirational right now. Other services consume inventory/prescription topics, but inventory and prescription themselves do not publish to those topics, so the event chain is incomplete in the current codebase.

**Q: What would you change first to make these services production-ready?**  
A: For inventory, I would formalize an availability DTO/contract, add reservation semantics if orders truly need them, and fix the JWKS URI. For prescription, I would either remove the unused external-verification config or implement the real outbound verification flow with timeouts, retries only where safe, and an outbox/event publisher if downstream events are required.

## Gotchas / real findings

- **Inventory REST surface does not match the API contract doc.** The contract says `GET /api/v1/inventory`, `PUT /api/v1/inventory/{pharmacyId}/{medicationId}`, and reservation endpoints. The real controller exposes `/stock-levels`, `/pharmacies/{pharmacyId}/products/{productId}`, `/pharmacies/{pharmacyId}/low-stock`, `/stock-levels/{id}/adjust`, and no reservation API.
- **The synchronous order->inventory contract is mismatched.** `InventoryAvailabilityClient` calls `/api/v1/inventory/pharmacies/{pharmacyId}/products/{medicationId}` and expects `InventoryAvailabilityResponse(boolean available, String reason)`, but `InventoryController.getPharmacyProductStock(...)` actually returns `StockLevelResponse`. That is a likely deserialization/runtime integration bug.
- **Naming drift: `productId` vs `medicationId`.** Inventory uses `productId` in entities, requests, and routes; order-service and the inventory availability endpoint query parameter use `medicationId`; docs mostly say medication. This is a classic cross-service vocabulary leak.
- **Inventory emits no Kafka events.** A repo-wide grep over `inventory-service/src/main` found no `KafkaTemplate`, no `StreamBridge`, and no `@KafkaListener`-driven producer path. So `pharmacy.inventory.events.v1` references in order/notification are currently waiting on a producer that does not exist.
- **Prescription emits no Kafka events either.** A repo-wide grep over `prescription-service/src/main` also found no Kafka producer code. That means `pharmacy.prescription.events.v1` consumers in notification are orphaned today.
- **Prescription workflow is much simpler than the prompt docs.** There is no external mock verification, no retry/circuit-breaker flow, no outbox table, no verify endpoint, and no reject endpoint. Instead, the implemented workflow is create -> activate -> fill -> expire.
- **Prescription authorization differs from the API contract.** The contract says customers can submit prescriptions and pharmacists can `POST /{id}/verify` or `/reject`. The real controller only allows creation to `STORE_MANAGER`, `ADMIN`, or `PHARMACIST`, and it offers `POST /{id}/activate` plus `PUT /{id}/lines/{lineId}/fill`.
- **`listByStatus` behaves awkwardly when `status` is omitted.** The controller allows `status` to be absent, but `PrescriptionService.listByStatus(...)` directly calls `prescriptionRepository.findByStatus(status, pageable)`. Passing `null` there is not an “all statuses” query; it will likely return nothing useful.
- **JWT validation config drifts in both services.** Inventory hard-codes `http://localhost:8080/.well-known/jwks.json`, which is missing `/api/v1/auth` and points at the gateway port. Prescription hard-codes `http://auth-service:8080/api/v1/auth/.well-known/jwks.json`, which uses the right path but still the wrong port, because auth-service runs on `8081`.
- **Prescription service config contains unused downstream URLs.** `services.customer-service.base-url`, `inventory-service.base-url`, and `product-service.base-url` are present in `application.yml`, but the service implementation does not use them.

## Trace-through

**Example: order creation checking inventory before saving the order**

1. `OrderService.createOrder()` iterates through every requested order item.
2. For each item it calls `InventoryAvailabilityClient.checkAvailability(pharmacyId, medicationId, quantity)`.
3. The client builds a URL of the form `/api/v1/inventory/pharmacies/{pharmacyId}/products/{medicationId}` against the configured inventory base URL (`http://inventory-service:8085` by default).
4. In `inventory-service`, `InventoryController.getPharmacyProductStock(...)` handles that request and delegates to `InventoryService.getPharmacyProductStock(...)`.
5. The service executes `StockLevelRepository.findByPharmacyIdAndProductId(pharmacyId, productId)` and throws `StockLevelNotFoundException` if no row exists.
6. If a row exists, the controller returns a `StockLevelResponse` containing `quantityOnHand`, reorder thresholds, and status.
7. Back in `order-service`, the code expects an `InventoryAvailabilityResponse` with just `available` and `reason` fields.
8. **Real-world gotcha:** because the wire contract is different on the two sides, this “preflight stock check” path is not cleanly aligned today.
9. If that DTO mismatch were corrected, order creation would reject unavailable items synchronously before persisting the order.
10. None of this flow produces inventory Kafka events, so downstream consumers waiting on `pharmacy.inventory.events.v1` still receive nothing from the inventory service itself.
