# REST API contracts

All public routes use `/api/v1`, JSON and `X-Correlation-ID`. UUID path values are strings in canonical UUID format. Collection endpoints accept `page`, `size`, `sort` and domain filters. Default maximum page size is 100.

## Common response conventions

- Create: `201 Created` with `Location` header.
- Read/update: `200 OK`.
- Delete/deactivate with no body: `204 No Content`.
- Validation: `400 Bad Request` using `ProblemDetail` plus field violations.
- Authentication failure: `401 Unauthorized`.
- Authorization failure: `403 Forbidden`.
- Missing resource: `404 Not Found`.
- State conflict/idempotency mismatch: `409 Conflict`.
- Rate limited: `429 Too Many Requests` with `Retry-After`.
- Dependency unavailable: sanitized `503 Service Unavailable` when appropriate.

## Roles

- `CUSTOMER`
- `PHARMACIST`
- `STORE_MANAGER`
- `ADMIN`

## Auth service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public/local learning | Create fictional customer login |
| POST | `/api/v1/auth/login` | Public | Issue access and refresh token |
| POST | `/api/v1/auth/refresh` | Public with refresh token | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Authenticated | Revoke refresh token |
| GET | `/api/v1/auth/.well-known/jwks.json` | Internal/public-key only | Publish RSA public key as JWKS |

Login response: `accessToken`, `tokenType`, `expiresInSeconds`, `refreshToken`, `roles`.

## Product service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/medications` | Authenticated | Search/filter/page medications |
| GET | `/api/v1/medications/{id}` | Authenticated | Get medication |
| POST | `/api/v1/medications` | PHARMACIST/ADMIN | Create medication |
| PUT | `/api/v1/medications/{id}` | PHARMACIST/ADMIN | Replace editable fields |
| PATCH | `/api/v1/medications/{id}/status` | ADMIN | Activate/deactivate |

Search filters: `q`, `manufacturer`, `dosageForm`, `active`.

## Customer service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/customers` | CUSTOMER/ADMIN | Create profile |
| GET | `/api/v1/customers/{id}` | Owner or staff | Get profile |
| PUT | `/api/v1/customers/{id}` | Owner or ADMIN | Update profile |
| GET | `/api/v1/customers/{id}/addresses` | Owner or staff | List addresses |
| POST | `/api/v1/customers/{id}/addresses` | Owner or ADMIN | Add address |

Owner checks compare the JWT `sub` value with `auth_user_id`; staff roles may
access only for the fictional workflow.

## Pharmacy service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/pharmacies` | Authenticated | Search locations |
| GET | `/api/v1/pharmacies/{id}` | Authenticated | Pharmacy details/hours |
| POST | `/api/v1/pharmacies` | ADMIN | Create pharmacy |
| PUT | `/api/v1/pharmacies/{id}` | STORE_MANAGER/ADMIN | Update pharmacy |
| PATCH | `/api/v1/pharmacies/{id}/status` | STORE_MANAGER/ADMIN | Open/close/disable |

Optional search filters: `postalCode`, `status`, `latitude`, `longitude`, `radiusMiles`. If geospatial logic is simplified, clearly document the approximation.

## Inventory service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/inventory/stock-levels/{id}` | Authenticated | Read one stock level |
| GET | `/api/v1/inventory/pharmacies/{pharmacyId}` | Authenticated | List pharmacy stock |
| GET | `/api/v1/inventory/pharmacies/{pharmacyId}/products/{productId}` | Authenticated | Read pharmacy/product stock |
| GET | `/api/v1/inventory/pharmacies/{pharmacyId}/low-stock` | Authenticated | List low-stock items |
| POST | `/api/v1/inventory/stock-levels` | STORE_MANAGER/ADMIN | Create a stock level |
| POST | `/api/v1/inventory/stock-levels/{id}/adjust` | PHARMACIST/STORE_MANAGER/ADMIN | Adjust stock with an audit record |
| GET | `/api/v1/inventory/stock-levels/{id}/history` | Authenticated | Read stock adjustments |
| GET | `/api/v1/inventory/availability` | Authenticated | Read availability for ordering |
| GET | `/api/v1/inventory/reservations/{orderId}` | Authenticated | Reservation status |

The service permits the availability endpoint internally without a JWT so
order-service can call it; the gateway still protects its public route.
Inventory reservation/release for orders occurs through Kafka, not a public
mutation endpoint.

## Prescription service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/prescriptions` | PHARMACIST/STORE_MANAGER/ADMIN | Create fictional prescription |
| GET | `/api/v1/prescriptions/{id}` | Authenticated | Read prescription |
| GET | `/api/v1/prescriptions?status={status}` | Authenticated | Page by optional status |
| GET | `/api/v1/prescriptions/customers/{customerId}` | Authenticated | Page by customer |
| POST | `/api/v1/prescriptions/{id}/activate` | PHARMACIST/ADMIN | Move `PENDING` to `ACTIVE` |
| PUT | `/api/v1/prescriptions/{id}/lines/{lineId}/fill` | PHARMACIST/ADMIN | Record dispensed quantity |

The implemented lifecycle is `PENDING -> ACTIVE -> FILLED` (or `EXPIRED`).
Customer ownership is not yet enforced on read routes; see `known-gaps.md`.

## Order service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/orders` | CUSTOMER/PHARMACIST | Create order; requires `Idempotency-Key` |
| GET | `/api/v1/orders/{id}` | Authenticated at gateway | Order details |
| GET | `/api/v1/orders/{id}/status` | Authenticated at gateway | Lightweight order status |
| GET | `/api/v1/orders?customerId={customerId}` | Authenticated | Page orders by customer |
| POST | `/api/v1/orders/{id}/cancel` | Authenticated | Cancel an eligible order |
| POST | `/api/v1/orders/{id}/ready` | PHARMACIST | Mark ready for pickup |
| POST | `/api/v1/orders/{id}/complete` | PHARMACIST | Complete pickup |

Create request: `customerId`, `prescriptionId`, `pharmacyId`, `paymentToken`, and one or more `{medicationId, quantity}` items. The service resolves authoritative prices; it never trusts client totals.
The current order controller does not complete owner/role authorization on
read/list/state-change routes; see `known-gaps.md`.

## Payment service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/payments` | Owner or staff | Direct simulated payment with idempotency support |
| GET | `/api/v1/payments/{id}` | Owner or staff | Payment status, never sensitive instrument data |
| GET | `/api/v1/payments/by-order/{orderId}` | Owner or staff | Payment status by order |
| POST | `/api/v1/payments/{id}/refund` | ADMIN | Simulated refund |

Authorization is driven by `InventoryReserved` events.

## Notification service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/notifications?customerId={customerId}` | Owner/staff | Page simulated delivery records for one customer; `customerId` filter required, ownership verified via customer-service |
| GET | `/api/v1/notifications/{id}` | Owner/staff | Delivery details |
| PUT | `/api/v1/notifications/{id}/read` | Owner/staff | Mark a notification read |
| GET | `/api/v1/notifications/templates` | ADMIN | List templates |

Notification responses include nullable `orderId` for notifications produced
from order/payment events. Notifications are created from implemented
order/payment events, with the order-to-customer mapping taken from
`OrderCreated`. Prescription handlers are wired, but prescription-service does
not yet produce those events. Email delivery is simulated locally
(`NOTIFICATION_EMAIL_MODE=simulated`); set it to `smtp` only with an explicitly
configured mail server.

## Audit service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/audit/{aggregateId}` | ADMIN | Page an aggregate's audit trail |
| GET | `/api/v1/audit/search` | ADMIN | Search by resource/action/user/service/time |
| GET | `/api/v1/audit/compliance/report` | ADMIN | Summarize a resource type over a time range |

## External mock service control endpoints

These routes are local/test only and must not be exposed by production-like gateway profiles.

| Method | Route | Purpose |
|---|---|---|
| PUT | `/api/v1/mock/prescription?mode={mode}&delayMs={ms}` | Set SUCCESS/REJECT/DELAY/ERROR behavior for the lab endpoint |
| PUT | `/api/v1/mock/payment?mode={mode}&delayMs={ms}` | Set SUCCESS/REJECT/DELAY/ERROR behavior |
| POST | `/api/v1/mock/reset` | Restore defaults |
| POST | `/api/v1/mock/process-payment` | Endpoint called by payment-service |
| GET | `/api/v1/mock/prescription` | Inspect the configured prescription lab response; not called by prescription-service |
| GET | `/api/v1/mock/payment` | Inspect the configured payment lab response |

## Problem response example

```json
{
  "type": "https://errors.pharmacy.local/invalid-state",
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Order cannot move from CANCELLED_PAYMENT to READY_FOR_PICKUP",
  "instance": "/api/v1/orders/2a6fd8ac-13cb-49c9-923a-3be064871c59/ready",
  "errorCode": "ORDER_INVALID_STATE",
  "correlationId": "0dc82941-b987-4e41-bf78-d307051e702a",
  "timestamp": "2026-09-16T12:00:00Z"
}
```
