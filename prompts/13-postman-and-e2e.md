# Prompt 13 — Postman collection and final E2E automation

## Give Copilot these files

- `docs/06-api-contracts.md`
- `docs/07-event-contracts.md`
- `docs/09-testing-strategy.md`
- `learning/definition-of-done.md`

## Prompt

```text
Create a complete Postman collection and environment templates for the final platform. Do not include real credentials or tokens.

The collection must:
- group requests by auth, product, customer, pharmacy, inventory, prescription, order, payment, notification, audit and mock controls;
- log in and store access/refresh tokens in environment variables without printing them;
- capture created UUIDs and correlation IDs;
- use deterministic fictional test data;
- include happy path from login through confirmed order/notification/audit;
- poll asynchronous order status with a bounded timeout;
- include negative folders for 400, 401, 403, 404, 409 and 429;
- include inventory rejection, payment failure, duplicate idempotency request, slow verification and external error scenarios;
- assert status, schema-critical fields, correlation header and final states;
- reset mock behavior after tests.

Also create a Newman-compatible command/script for CI/local use and a shell smoke-test script using curl for health/login/product/order essentials. Scripts must fail fast, avoid echoing secrets and print useful sanitized diagnostics.

Generate docs/e2e-runbook.md describing prerequisites, test order, expected events/states, troubleshooting and cleanup.
```

## Final E2E scenarios

1. Happy path -> `CONFIRMED`, reservation `COMMITTED`, payment `AUTHORIZED`, notification `SENT`.
2. Insufficient stock -> `CANCELLED_INVENTORY`, no payment.
3. Payment failure -> `CANCELLED_PAYMENT`, reservation `RELEASED`.
4. Duplicate order request -> same order, no duplicate reservation/payment.
5. Slow verification -> timeout/circuit behavior with trace evidence.
6. Unauthorized/forbidden matrix.
7. Poison notification -> retry/DLT while order remains confirmed.

## Acceptance criteria

- Collection runs from a clean documented seed/reset state.
- Secrets are environment placeholders.
- Async polling is bounded.
- Failures return non-zero in Newman/CI.
- E2E run produces correlation IDs that can be followed in dashboards/logs.

