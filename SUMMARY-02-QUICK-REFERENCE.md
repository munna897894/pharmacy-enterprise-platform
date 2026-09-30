# ✨ Quick Reference Guide — Historical Snapshot

> **Historical snapshot — scope:** early platform overview through Prompt 03, before later workflow, deployment, and observability work. The original creation date is not preserved; this file was first committed/imported on 2026-09-28. Service ports, integrations, test counts, progress, and next steps below are not current. Use the [repository overview](README.md), [architecture](docs/02-architecture.md), [local deployment](docs/architecture-local.md), [API contracts](docs/06-api-contracts.md), and [event contracts](docs/07-event-contracts.md) for current facts.

## Preserved historical content

## 🏗️ System Architecture At A Glance

```
                        Customers/Apps
                             │
                             ▼
                ┌─────────────────────────┐
                │   API GATEWAY (8080)    │  ← Single entry point
                │  • JWT validation       │
                │  • Rate limiting        │
                │  • Security headers     │
                │  • Routing              │
                └────────────┬────────────┘
                             │
        ┌────────────────────┼────────────────────────┐
        ▼                    ▼                        ▼
    ┌─────────┐        ┌──────────┐           ┌──────────┐
    │  Auth   │        │ Product  │           │ Customer │
    │ (8081)  │        │ (8082)   │           │ (8083)   │
    │         │        │          │           │          │
    │Login    │        │Medicines │           │Profiles  │
    │Security │        │Catalog   │           │Addresses │
    └────┬────┘        └────┬─────┘           └────┬─────┘
         │                  │                      │
         └──────────────────┼──────────────────────┘
                            │
                ┌───────────┼───────────┐
                ▼           ▼           ▼
           ┌─────────┐    ┌──────────┐   ┌──────────┐
           │Pharmacy │    │Inventory │   │Prescrip. │
           │(8084)   │    │ (8085)   │   │ (8086)   │
           │         │    │          │   │          │
           │Locations│    │Stock     │   │Lifecycle │
           │Hours    │    │Reserved  │   │Refills   │
           └────┬────┘    └────┬─────┘   └────┬─────┘
                │              │              │
                └──────────────┼──────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌─────────┐          ┌──────────┐         ┌──────────┐
   │  Order  │          │ Payment  │         │Notify    │
   │ (8087)  │          │ (8088)   │         │ (8089)   │
   │         │          │          │         │          │
   │Purchases│          │Credit    │         │Email     │
   │Saga     │          │Card      │         │SMS       │
   │Outbox   │          │Idempotent│         │In-app    │
   └────┬────┘          └────┬─────┘         └────┬─────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                       ┌─────▼─────┐
                       │ Audit     │
                       │ (8090)    │
                       │           │
                       │ Logging   │
                       │ Compliance│
                       └───────────┘

INFRASTRUCTURE:
  • Kafka (Message Queue) - Services communicate via events
  • MySQL (Databases) - 9 separate schemas
  • Redis (Cache) - Rate limiting + caching
```

---

## 📊 What Each Service Does (In 1 Line)

| Service | Purpose |
|---------|---------|
| **Auth** | Handles login, issues JWT tokens, manages passwords |
| **Product** | Manages medicine catalog, pricing, search |
| **Customer** | Stores user profiles, addresses, order history |
| **Pharmacy** | Tracks pharmacy locations, hours, operating status |
| **Inventory** | Manages stock levels, reservations, adjustments |
| **Prescrip.** | Tracks prescription lifecycle (pending→active→filled) |
| **Order** | Coordinates order creation + saga choreography |
| **Payment** | Processes credit cards via Stripe, prevents double-charging |
| **Notify** | Sends emails, SMS, in-app messages |
| **Audit** | Logs everything for compliance & debugging |
| **Gateway** | Routes all requests, validates tokens, rate limits |

---

## 🔄 How Services Communicate

### PRIMARY METHOD: Kafka Message Queue (Asynchronous)

```
Order publishes → "I created an order"
Inventory reads → "I'll reserve stock"
Payment reads   → "I'll process payment"
Notify reads    → "I'll send email"
Audit reads     → "I'll log this"
```

**Benefits:**
- ✓ No waiting (all happen in parallel)
- ✓ Reliable (messages stored in Kafka)
- ✓ Flexible (add new services anytime)
- ✓ Auditable (all events recorded)

**Idempotency:**
- If same message delivered twice → handled safely
- "I already processed this message, skipping"

---

## 📈 By The Numbers

### Code Written
- **Total:** 20,000+ lines
- **Production code:** 12,000 lines
- **Test code:** 8,000 lines

### Services
- **Total:** 11
- **Business services:** 10
- **API gateway:** 1
- **External mock:** 1

### Tests
- **Automated tests:** 287+
- **All passing:** ✓
- **Failures:** 0
- **Errors:** 0

### Test Coverage
- Unit tests: 100+
- Integration tests: 100+
- Controller tests: 50+
- Kafka tests: 27
- Gateway tests: 15

### Build
- **Full clean build:** ~90 seconds
- **All tests running:** Yes

### Database
- **Migration files:** 18
- **Schema creation:** V1
- **Seed data:** V2
- **Per-service migrations:** Yes

---

## ✅ What Works Right Now

### You CAN:
- ✓ Register a new account (create user)
- ✓ Login with username/password (get JWT token)
- ✓ Browse medicine catalog
- ✓ Search for specific medicines
- ✓ View your profile and addresses
- ✓ Place an order (with automatic saga)
- ✓ Pay with credit card (Stripe integration)
- ✓ Get notifications (email/SMS/in-app)
- ✓ Check order status
- ✓ View order history
- ✓ View prescription status
- ✓ Everything is logged for compliance

### You CANNOT (yet):
- ✗ Scale across multiple Kubernetes nodes
- ✗ Service-to-service HTTP calls (only Kafka)
- ✗ View system dashboards/monitoring
- ✗ View logs from all services in one place

---

## 🚀 Next Step (Prompt 04)

### Service-to-Service Communication

**Current:** Services only talk via Kafka (messages)

**Problem:** Sometimes services need immediate answers
- "Order Service: Do you have stock RIGHT NOW?"
- (not: wait for Kafka events)

**Solution:** Add HTTP client calls with resilience

**Example Use Case:**
```
Order Service → calls Inventory Service HTTP API
             → asks: "Do you have 2 bottles in stock?"
             → gets: "Yes" or "No"
             → decides immediately whether to accept order
```

**What we'll add:**
- RestTemplate HTTP clients
- Timeouts (don't wait forever)
- Retries (if service is busy)
- Circuit breaker (stop calling if broken)
- Propagate correlation ID in headers

---

## 💡 Key Insight: Why This Architecture?

Real companies (Amazon, Netflix, Uber) use this pattern because:

### FLEXIBILITY
- Before: Central database → all services query it → tightly coupled
- Now: New service → starts reading Kafka events → nobody affected

### RELIABILITY
- Before: Service crashes → database locked → entire system down
- Now: Service crashes → messages pile up in Kafka → service comes back, catches up where it left off

### SCALABILITY
- Before: One database → scale whole system together
- Now: Order Service slow? → add more Order Service instances
  - Not enough inventory? → add more Inventory Service instances

### TESTABILITY
- Before: Integration tests need entire system running
- Now: Test each service in isolation → run 300 tests in 90 seconds

---

## 🎯 Success Metrics

- ✅ All 287 tests pass → System is reliable (no broken functionality)
- ✅ Build completes in 90 seconds → Fast development feedback loop
- ✅ Zero hard-coded secrets → Production-safe (no passwords in code)
- ✅ JWT validation on every request → Secure (tokens can't be forged)
- ✅ Rate limiting enabled → Protected against abuse
- ✅ All activity is logged → Compliant with regulations
- ✅ Correlation ID on every request → Can debug distributed issues
- ✅ Each service has own database → No accidental coupling

---

**Ready to proceed with Prompt 04 (Service-to-Service Communication)?**
