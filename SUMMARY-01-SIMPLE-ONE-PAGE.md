# 🎯 The Pharmacy Project - One Page Summary

> **Historical snapshot — scope:** early project overview through Prompt 03, before the later service and platform work. The original creation date is not preserved; this file was first committed/imported on 2026-09-28. Progress, test counts, topology, and “what’s next” below are not current. Use the [repository overview](README.md), [architecture](docs/02-architecture.md), [local deployment](docs/architecture-local.md), [AWS sandbox status](docs/architecture-cloud.md), and [E2E runbook](docs/e2e-runbook.md) for current facts.

## Preserved historical content

## What Are We Building?
An online pharmacy where customers can order medicine, pay, and get notifications.
**Like:** Uber + Stripe + Amazon, but for pharmacy

---

## The Simple Version

```
                ┌─ Customer uses app
                │
                ▼
      ┌─────────────────┐
      │  API Gateway    │  ← Checks ID (JWT token)
      │  (Port 8080)    │  ← Stops spam (rate limit)
      └────────┬────────┘
               │
     ┌─────────┴─────────────────┐
     │                           │
     ▼                           ▼
  ┌─────────┐                ┌──────────┐
  │Auth     │                │Product   │  ... 8 more services
  │Service  │                │Service   │  (Inventory, Order, Payment,
  │(Login)  │                │(Catalog) │   Notify, Audit, etc.)
  └────┬────┘                └────┬─────┘
       │                         │
       ▼─────────Kafka Events────▼
       (Services send messages: "I created an order", etc.)
```

---

## Progress So Far

- ✅ **Phase 1:** Built the foundation
  - Project structure, build system

- ✅ **Phase 2:** Built 11 services
  - Auth (login)
  - Product (medicine catalog)
  - Customer, Pharmacy, Inventory, Prescription
  - Order (with automatic workflow)
  - Payment (credit card processing)
  - Notification (emails & SMS)
  - Audit (compliance logging)
  - **287 tests all passing ✓**

- ✅ **Phase 3:** Built the front door (API Gateway)
  - Single entry point, validates tokens, prevents spam

- ➡️ **Phase 4:** Services talking to each other (HTTP calls) ← YOU ARE HERE
  - Phase 5: Kubernetes containers
  - Phase 6: Monitoring dashboards

---

## How Does an Order Work?

1. Customer: "Order 2 Aspirin"
2. Gateway: "Are you logged in?" → Yes ✓
3. Order Service: "Creating order..."
4. Kafka broadcasts: "New order created!"
5. Inventory Service hears: "Reserve 2 Aspirin"
6. Payment Service hears: "Charge credit card"
7. Notification Service hears: "Send email confirmation"
8. Audit Service hears: "Log everything"

All automatic. All reliable. Done!

---

## By The Numbers

| Metric | Value |
|--------|-------|
| Lines of Code | 20,000+ |
| Services | 11 (working right now) |
| Automated Tests | 287+ (all passing ✓) |
| Hard-coded Passwords | 0 (secure ✓) |
| Build Time | ~90 seconds |

### Can Handle
- ✓ User login
- ✓ Medicine browsing
- ✓ Order placement
- ✓ Credit card payment
- ✓ Email notifications
- ✓ Complete audit trail

---

## What's Next?

**Prompt 04:** Let services call each other over HTTP

**Why?** Sometimes a service needs an immediate answer:
- Order Service → "Do you have stock?" (not: wait for Kafka message)
- Inventory Service → "Yes, 5 bottles"

**Then:**
- Prompt 05: Package as Docker containers → Run on Kubernetes
- Prompt 06: Monitoring dashboards → See system health
- etc.

---

## Key Insight

We're building like Netflix/Amazon:
- Each service independent (own database)
- Communicate via messages (Kafka)
- Automatically scale (more instances)
- Can update each service alone
- If one breaks, others still work

---

## Success Checklist ✅

- ✅ All 287 tests pass
- ✅ No broken functionality
- ✅ Fast build (90 seconds)
- ✅ Secure (JWT tokens, rate limiting)
- ✅ Everything logged
- ✅ Can track requests across services
- ✅ Ready for Phase 4

---

**Ready for Prompt 04? Say "Go ahead"!**
