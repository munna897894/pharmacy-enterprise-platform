# Scope and architecture decisions

## Product scenario

The platform models a simplified pharmacy order journey:

1. A user authenticates.
2. The user searches medications and nearby pharmacies.
3. A prescription is submitted and verified through a controllable external-system simulator.
4. An order is created for a verified prescription.
5. Inventory is reserved asynchronously.
6. Payment is authorized asynchronously.
7. The order becomes confirmed or cancelled.
8. Notification and audit services record relevant events.

Only fictional data is permitted.

## Services in scope

| Component | Responsibility | Durable store |
|---|---|---|
| `api-gateway` | Routing, JWT enforcement, CORS, rate limiting, correlation ID | Redis for rate limiting |
| `auth-service` | Users, password hashes, roles, access/refresh tokens, public key endpoint | `auth_db` |
| `product-service` | Medication catalog, search and price metadata | `product_db` + Redis cache |
| `customer-service` | Fictional customer profiles | `customer_db` |
| `pharmacy-service` | Pharmacy locations and operating status | `pharmacy_db` |
| `inventory-service` | Stock, reservation and release | `inventory_db` |
| `prescription-service` | Submission, verification and status | `prescription_db` |
| `order-service` | Order aggregate and saga status | `order_db` |
| `payment-service` | Simulated payment authorization/refund | `payment_db` |
| `notification-service` | Simulated email/SMS delivery records | `notification_db` |
| `audit-service` | Append-only business audit view | `audit_db` |
| `external-mock-service` | Controllable insurance/prescriber/payment-like failures and delays | In-memory only |

## Fixed decisions

- Monorepo with independent Maven service modules and one root aggregator POM.
- Database per service in principle; one MySQL container with separate schemas locally.
- No distributed database transactions.
- Choreography saga for the order workflow.
- Transactional outbox in services that publish state-changing business events.
- Idempotent inbox/processed-event handling in consumers.
- Kafka in KRaft mode; no ZooKeeper and no Amazon MSK.
- Kubernetes-native service discovery; no Eureka.
- Local-first development. AWS is an optional, temporary mapping exercise.
- No user interface. Postman/curl act as the client.
- No real email, SMS, insurer, prescriber, drug database or payment provider.
- No production PHI/HIPAA claim. Security controls are educational demonstrations.

## Required end-to-end happy path

```text
Login -> JWT -> Gateway -> Search medication -> Submit prescription
-> External verification -> Create order -> OrderCreated
-> InventoryReserved -> PaymentCompleted -> OrderConfirmed
-> NotificationSent + Audit entries
```

## Required negative paths

- Invalid/expired JWT returns 401.
- Correct JWT with wrong role returns 403.
- Invalid prescription is rejected.
- Insufficient inventory cancels the order.
- Payment failure releases reserved inventory and cancels the order.
- Duplicate Kafka delivery changes business state only once.
- Slow external verification triggers timeout/circuit breaker/fallback behavior.
- Database outage produces a controlled error and health degradation.

## Non-goals for the 15-day build

- Frontend or mobile UI
- Real patient data, insurance claims, controlled-substance compliance or e-prescribing certification
- Real credit-card handling or PCI compliance
- Multi-region production deployment
- Service mesh
- Schema registry platform
- Full OAuth/OIDC provider such as Keycloak (valuable follow-up, not core scope)
- Long-running paid AWS infrastructure

## Optional stretch items

Only attempt these after all acceptance criteria pass:

- Keycloak replacing the educational auth service
- Avro/Protobuf plus Schema Registry
- Contract testing with Pact or Spring Cloud Contract
- Canary deployment with Argo Rollouts
- Vault/External Secrets Operator
- Multi-broker Kafka and chaos testing

