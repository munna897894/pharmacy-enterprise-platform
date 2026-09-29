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

Owner checks compare JWT `userId` with `auth_user_id`; staff roles may access only for the fictional workflow.

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
| GET | `/api/v1/inventory` | PHARMACIST/STORE_MANAGER/ADMIN | Query stock by pharmacy/medication |
| PUT | `/api/v1/inventory/{pharmacyId}/{medicationId}` | STORE_MANAGER/ADMIN | Set/adjust stock with optimistic lock |
| GET | `/api/v1/inventory/availability` | Authenticated | Read availability for ordering |
| GET | `/api/v1/inventory/reservations/{orderId}` | Staff | Reservation status |

Inventory reservation/release for orders occurs through Kafka, not a public mutation endpoint.

## Prescription service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/prescriptions` | CUSTOMER/PHARMACIST | Submit fictional prescription |
| GET | `/api/v1/prescriptions/{id}` | Owner or staff | Read status |
| GET | `/api/v1/prescriptions` | Staff; customer sees own | Search/page |
| POST | `/api/v1/prescriptions/{id}/verify` | PHARMACIST | Trigger verification |
| POST | `/api/v1/prescriptions/{id}/reject` | PHARMACIST | Manual rejection with reason |

The verification command is idempotent. A verified/rejected prescription cannot be re-verified without an explicit future feature.

## Order service

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/v1/orders` | CUSTOMER/PHARMACIST | Create order; requires `Idempotency-Key` |
| GET | `/api/v1/orders/{id}` | Owner or staff | Order and status history |
| GET | `/api/v1/orders` | Customer own/staff search | Page orders |
| POST | `/api/v1/orders/{id}/ready` | PHARMACIST | Mark ready for pickup |
| POST | `/api/v1/orders/{id}/complete` | PHARMACIST | Complete pickup |

Create request: `customerId`, `prescriptionId`, `pharmacyId`, `paymentToken`, and one or more `{medicationId, quantity}` items. The service resolves authoritative prices; it never trusts client totals.

## Payment service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/payments/{id}` | Owner or staff | Payment status, never sensitive instrument data |
| GET | `/api/v1/payments/by-order/{orderId}` | Owner or staff | Payment status by order |
| POST | `/api/v1/payments/{id}/refund` | ADMIN | Simulated refund |

Authorization is driven by `InventoryReserved` events.

## Notification service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/notifications` | Owner/staff | Page simulated delivery records |
| GET | `/api/v1/notifications/{id}` | Owner/staff | Delivery details |

## Audit service

| Method | Route | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/audit` | ADMIN | Search by aggregate/event/correlation/time |
| GET | `/api/v1/audit/{id}` | ADMIN | Read sanitized entry |

## External mock service control endpoints

These routes are local/test only and must not be exposed by production-like gateway profiles.

| Method | Route | Purpose |
|---|---|---|
| PUT | `/mock/config/prescription-verification` | Set SUCCESS/REJECT/DELAY/ERROR behavior |
| PUT | `/mock/config/payment` | Set SUCCESS/FAIL/DELAY/ERROR behavior |
| POST | `/mock/reset` | Restore defaults |
| POST | `/mock/prescriptions/verify` | Endpoint called by prescription service |

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

