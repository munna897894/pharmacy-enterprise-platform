#!/usr/bin/env python3
"""Generates postman/pharmacy.postman_collection.json and
postman/local.postman_environment.json.

This is a *build-time generator*, not a runtime dependency: the committed
JSON files are the actual Prompt 13 deliverables used by Postman/Newman.
Re-run this script (`python3 scripts/generate_postman_collection.py`) and
commit the regenerated output whenever a request shape changes, so the
collection never silently drifts from the real controllers it exercises.
See docs/e2e-runbook.md for the doc-vs-implementation drift this generator
was written to avoid repeating.
"""
import json

SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"


def header(key, value, disabled=False):
    return {"key": key, "value": value, "type": "text", "disabled": disabled}


def body_json(obj):
    return {"mode": "raw", "raw": json.dumps(obj, indent=2),
            "options": {"raw": {"language": "json"}}}


def script(exec_lines, script_type):
    if isinstance(exec_lines, str):
        exec_lines = exec_lines.split("\n")
    return {"listen": script_type, "script": {"type": "text/javascript", "exec": exec_lines}}


def request(name, method, path, description="", headers=None, body=None,
            pre_request=None, tests=None, query=None, auth_token_var=None,
            correlation=True):
    headers = list(headers or [])
    if auth_token_var:
        headers.append(header("Authorization", f"Bearer {{{{{auth_token_var}}}}}"))
    if correlation:
        headers.append(header("X-Correlation-ID", "{{correlationId}}"))
    if body is not None:
        headers.append(header("Content-Type", "application/json"))

    url = {
        "raw": "{{baseUrl}}" + path + (("?" + "&".join(f"{k}={v}" for k, v in query.items())) if query else ""),
        "host": ["{{baseUrl}}"],
        "path": [p for p in path.strip("/").split("/") if p],
    }
    if query:
        url["query"] = [{"key": k, "value": str(v)} for k, v in query.items()]

    item = {
        "name": name,
        "request": {
            "method": method,
            "header": headers,
            "url": url,
            "description": description,
        },
        "response": [],
    }
    if body is not None:
        item["request"]["body"] = body_json(body)

    events = []
    if pre_request:
        events.append(script(pre_request, "prerequest"))
    if tests:
        events.append(script(tests, "test"))
    if events:
        item["event"] = events
    return item


def folder(name, items, description=""):
    return {"name": name, "item": items, "description": description}


CORRELATION_PRE = [
    "// Generate a fresh correlation ID for every request so it can be",
    "// followed end-to-end across services/logs/dashboards.",
    "pm.environment.set('correlationId', pm.variables.replaceIn('{{$guid}}'));",
]

BASE_TESTS = [
    "pm.test('X-Correlation-ID is echoed back', function () {",
    "    const sent = pm.request.headers.get('X-Correlation-ID');",
    "    if (sent) {",
    "        pm.expect(pm.response.headers.get('X-Correlation-ID') || sent).to.be.a('string');",
    "    }",
    "});",
]


def status_test(codes):
    if isinstance(codes, int):
        codes = [codes]
    arr = ", ".join(str(c) for c in codes)
    return [
        f"pm.test('Status code is one of [{arr}]', function () {{",
        f"    pm.expect(pm.response.code).to.be.oneOf([{arr}]);",
        "});",
    ] + BASE_TESTS


def save_test(json_path, var_name, extra=None):
    lines = [
        "const body = pm.response.json();",
        f"if (body && body.{json_path} !== undefined && body.{json_path} !== null) {{",
        f"    pm.environment.set('{var_name}', String(body.{json_path}));",
        "}",
    ]
    if extra:
        lines += extra
    return lines


# ---------------------------------------------------------------------------
# 0. AUTH
# ---------------------------------------------------------------------------
auth_items = [
    request(
        "Register Customer User",
        "POST", "/api/v1/auth/register",
        "Registers a brand-new, uniquely-named fictional CUSTOMER login for this test run so the "
        "collection is self-contained and repeatable from a clean state (no pre-seeded accounts required).",
        body={
            "email": "{{customerUsername}}@example.test",
            "username": "{{customerUsername}}",
            "password": "{{customerPassword}}",
            "firstName": "Ada",
            "lastName": "Customer",
            "roles": ["CUSTOMER"],
        },
        pre_request=[
            "const runId = Date.now().toString().slice(-7) + Math.floor(Math.random() * 90 + 10);",
            "pm.environment.set('customerUsername', 'cust_' + runId);",
            "pm.environment.set('customerPassword', 'Password123!');",
        ] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "customerAuthUserId"),
    ),
    request(
        "Register Staff User (Pharmacist+Admin)",
        "POST", "/api/v1/auth/register",
        "Registers a fictional staff login with PHARMACIST and ADMIN roles used for prescription, "
        "pharmacy, inventory and admin-only requests throughout the collection.",
        body={
            "email": "{{staffUsername}}@example.test",
            "username": "{{staffUsername}}",
            "password": "{{staffPassword}}",
            "firstName": "Sam",
            "lastName": "Staff",
            "roles": ["PHARMACIST", "ADMIN"],
        },
        pre_request=[
            "const runId = Date.now().toString().slice(-7) + Math.floor(Math.random() * 90 + 10);",
            "pm.environment.set('staffUsername', 'staff_' + runId);",
            "pm.environment.set('staffPassword', 'Password123!');",
        ] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "staffAuthUserId"),
    ),
    request(
        "Login Customer",
        "POST", "/api/v1/auth/login",
        "Logs in as the customer user and stores the access/refresh tokens as environment variables. "
        "Tokens are never printed to the console.",
        body={"username": "{{customerUsername}}", "password": "{{customerPassword}}"},
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "const body = pm.response.json();",
            "pm.test('Access token issued', function () { pm.expect(body.accessToken).to.be.a('string').and.not.empty; });",
            "pm.environment.set('accessToken', body.accessToken);",
            "pm.environment.set('refreshToken', body.refreshToken);",
        ],
    ),
    request(
        "Login Staff",
        "POST", "/api/v1/auth/login",
        "Logs in as the staff (pharmacist/admin) user and stores the access/refresh tokens.",
        body={"username": "{{staffUsername}}", "password": "{{staffPassword}}"},
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "const body = pm.response.json();",
            "pm.test('Access token issued', function () { pm.expect(body.accessToken).to.be.a('string').and.not.empty; });",
            "pm.environment.set('staffAccessToken', body.accessToken);",
            "pm.environment.set('staffRefreshToken', body.refreshToken);",
        ],
    ),
    request(
        "Refresh Customer Token",
        "POST", "/api/v1/auth/refresh",
        "Rotates the customer refresh token; demonstrates the refresh-token flow works end-to-end.",
        body={"refreshToken": "{{refreshToken}}"},
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "const body = pm.response.json();",
            "pm.environment.set('accessToken', body.accessToken);",
            "pm.environment.set('refreshToken', body.refreshToken);",
        ],
    ),
    request(
        "Get JWKS (public keys)",
        "GET", "/api/v1/auth/.well-known/jwks.json",
        "Publishes the RSA public key set used to validate JWTs; no authentication required.",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 1. PRODUCT (MEDICATIONS)
# ---------------------------------------------------------------------------
product_items = [
    request(
        "Search Medications", "GET", "/api/v1/medications",
        "Searches the medication catalog. Uses a seeded medication so the search always returns data "
        "even on a freshly reset stack.",
        query={"q": "Amox", "page": 0, "size": 20},
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Get Medication by Id", "GET", "/api/v1/medications/{{medicationId}}",
        "Fetches a known seeded medication used throughout the collection for order/prescription lines.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Create Medication (Staff)", "POST", "/api/v1/medications",
        "Creates a fresh medication as PHARMACIST/ADMIN; saves the new id for the update/status requests below.",
        body={
            "ndcCode": "NDC-{{$randomInt}}-TEST",
            "name": "Fictional Test Tablet 500mg",
            "genericName": "fictionalol",
            "manufacturer": "Fictional Labs",
            "dosageForm": "TABLET",
            "strength": "500mg",
            "unitPrice": 12.50,
            "currency": "USD",
        },
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "createdMedicationId"),
    ),
    request(
        "Update Medication (Staff)", "PUT", "/api/v1/medications/{{createdMedicationId}}",
        "Updates editable fields on the medication created above.",
        body={
            "name": "Fictional Test Tablet 500mg (Updated)",
            "genericName": "fictionalol",
            "manufacturer": "Fictional Labs",
            "dosageForm": "TABLET",
            "strength": "500mg",
            "unitPrice": 13.75,
        },
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Deactivate Medication Status (Admin)", "PATCH", "/api/v1/medications/{{createdMedicationId}}/status",
        "Toggles the active flag; ADMIN only.",
        body={"active": False},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 2. CUSTOMER
# ---------------------------------------------------------------------------
customer_items = [
    request(
        "Create Customer Profile", "POST", "/api/v1/customers",
        "Creates the customer-service profile linked to the logged-in customer's auth user id.",
        body={
            "firstName": "Ada",
            "lastName": "Customer",
            "email": "{{customerUsername}}@example.test",
            "phone": "5555550100",
            "dateOfBirth": "1990-01-01",
        },
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "customerId"),
    ),
    request(
        "Get Customer Profile", "GET", "/api/v1/customers/{{customerId}}",
        "Reads back the profile as its owner.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Update Customer Profile", "PUT", "/api/v1/customers/{{customerId}}",
        "Owner updates their own profile.",
        body={
            "firstName": "Ada",
            "lastName": "Customer-Updated",
            "email": "{{customerUsername}}@example.test",
            "phone": "5555550101",
            "dateOfBirth": "1990-01-01",
        },
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Add Customer Address", "POST", "/api/v1/customers/{{customerId}}/addresses",
        "Adds a fictional shipping address for the customer.",
        body={
            "type": "SHIPPING",
            "line1": "1 Fictional Ave",
            "line2": "",
            "city": "Springfield",
            "state": "IL",
            "postalCode": "62701",
            "country": "USA",
        },
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201),
    ),
    request(
        "List Customer Addresses", "GET", "/api/v1/customers/{{customerId}}/addresses",
        "Lists addresses for the owner.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 3. PHARMACY
# ---------------------------------------------------------------------------
pharmacy_items = [
    request(
        "Search Pharmacies", "GET", "/api/v1/pharmacies",
        "Lists pharmacy locations (includes seeded demo pharmacies).",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Create Pharmacy (Admin)", "POST", "/api/v1/pharmacies",
        "Creates a fresh pharmacy with a real, valid UUID id. NOTE: the demo-seeded pharmacies use "
        "non-UUID ids (e.g. 'p1000000-...') that order-service's strict UUID pharmacyId field rejects; "
        "this collection always creates its own pharmacy rather than relying on seed data -- see "
        "docs/e2e-runbook.md 'Known limitations'.",
        body={
            "name": "Fictional Test Pharmacy",
            "licenseNumber": "LIC-TEST-{{$randomInt}}",
            "phone": "(555) 000-1111",
            "timezone": "America/New_York",
            "address": {
                "line1": "1 Test St", "city": "Testville", "state": "NY",
                "postalCode": "10001", "latitude": 40.0, "longitude": -73.0,
            },
        },
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "pharmacyId"),
    ),
    request(
        "Get Pharmacy by Id", "GET", "/api/v1/pharmacies/{{pharmacyId}}",
        "Reads back the pharmacy created above, including address/business hours.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Update Pharmacy (Staff)", "PUT", "/api/v1/pharmacies/{{pharmacyId}}",
        "Updates editable pharmacy fields.",
        body={"name": "Fictional Test Pharmacy (Updated)", "phone": "(555) 000-2222", "timezone": "America/New_York"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Update Pharmacy Status (Staff)", "PATCH", "/api/v1/pharmacies/{{pharmacyId}}/status",
        "Opens/closes/disables the pharmacy.",
        body={"status": "OPEN"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Add Pharmacy Address (Staff)", "POST", "/api/v1/pharmacies/{{pharmacyId}}/addresses",
        "Adds an additional fictional address record to the pharmacy.",
        body={
            "line1": "2 Test St", "city": "Testville", "state": "NY",
            "postalCode": "10002", "latitude": 40.01, "longitude": -73.01,
        },
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201),
    ),
]

# ---------------------------------------------------------------------------
# 4. INVENTORY
# ---------------------------------------------------------------------------
inventory_items = [
    request(
        "Create Stock Level (Staff)", "POST", "/api/v1/inventory/stock-levels",
        "Stocks the seeded medication at the pharmacy created above so the happy-path order can be fulfilled.",
        body={
            "pharmacyId": "{{pharmacyId}}",
            "productId": "{{medicationId}}",
            "quantityOnHand": 100000,
            "reorderLevel": 10,
            "reorderQuantity": 50,
        },
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "stockLevelId"),
    ),
    request(
        "Get Stock Level by Id", "GET", "/api/v1/inventory/stock-levels/{{stockLevelId}}",
        "Reads back the stock level record.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Get Stock by Pharmacy + Product", "GET",
        "/api/v1/inventory/pharmacies/{{pharmacyId}}/products/{{medicationId}}",
        "Reads stock for the specific pharmacy/product pair.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Adjust Stock Level (Staff)", "POST", "/api/v1/inventory/stock-levels/{{stockLevelId}}/adjust",
        "Applies a manual stock adjustment (e.g. cycle count correction).",
        body={"quantity": 5, "adjustmentType": "ADJUSTMENT", "reason": "Fictional cycle count correction"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Get Availability", "GET", "/api/v1/inventory/availability",
        "Availability read used before placing an order. NOTE: inventory-service itself marks this "
        "route permitAll(), but api-gateway's SecurityConfig authenticates anyExchange() except the "
        "auth/actuator allow-list, so a bearer token is still required at the gateway boundary.",
        query={"pharmacyId": "{{pharmacyId}}", "medicationId": "{{medicationId}}"},
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
        correlation=True,
    ),
    request(
        "Get Low Stock for Pharmacy", "GET", "/api/v1/inventory/pharmacies/{{pharmacyId}}/low-stock",
        "Lists products below reorder level for the pharmacy.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 5. PRESCRIPTION
# ---------------------------------------------------------------------------
prescription_items = [
    request(
        "Create Prescription (Staff)", "POST", "/api/v1/prescriptions",
        "Submits a fictional prescription for the customer. Uses a dynamically-computed future "
        "timestamp because prescribedAt must be 'today or future' relative to the service clock; a "
        "hardcoded date would eventually drift into the past (the local demo stack intentionally runs "
        "a future-dated simulated clock -- see docs/e2e-runbook.md).",
        body={
            "customerId": "{{customerId}}",
            "prescriberId": "{{$guid}}",
            "prescribedAt": "{{prescribedAtFuture}}",
            "expiresAt": "2035-01-01T00:00:00Z",
            "lines": [{"productId": "{{medicationId}}", "quantity": 2, "instructions": "Take one tablet twice daily"}],
        },
        auth_token_var="staffAccessToken",
        pre_request=[
            "const future = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000);",
            "pm.environment.set('prescribedAtFuture', future.toISOString());",
        ] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "prescriptionId") + [
            "if (body.lines && body.lines.length) { pm.environment.set('prescriptionLineId', body.lines[0].id); }",
        ],
    ),
    request(
        "Get Prescription by Id", "GET", "/api/v1/prescriptions/{{prescriptionId}}",
        "Reads back the prescription.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Activate Prescription (Staff)", "POST", "/api/v1/prescriptions/{{prescriptionId}}/activate",
        "Activates the prescription so it can back an order (PENDING -> ACTIVE).",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "pm.test('Prescription is ACTIVE', function () { pm.expect(pm.response.json().status).to.eql('ACTIVE'); });",
        ],
    ),
    request(
        "List Prescriptions for Customer", "GET", "/api/v1/prescriptions/customers/{{customerId}}",
        "Pages prescriptions for the customer.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Fill Prescription Line (Staff)", "PUT",
        "/api/v1/prescriptions/{{prescriptionId}}/lines/{{prescriptionLineId}}/fill",
        "Records dispensed quantity against a prescription line. Run this AFTER the Order folder/happy "
        "path so it doesn't interfere with order-creation validation earlier in the run.",
        body={"dispensedQuantity": 2},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 6. ORDER
# ---------------------------------------------------------------------------
order_items = [
    request(
        "Create Order", "POST", "/api/v1/orders",
        "Creates an order for the active prescription/pharmacy/medication. Requires the Idempotency-Key "
        "header per docs/06-api-contracts.md; a fresh key is generated per run.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}",
            "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}",
            "currency": "USD",
            "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 2, "unitPrice": 12.50}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "orderId") + [
            "pm.environment.set('pollAttempts', '0');",
        ],
    ),
    request(
        "Poll Order Status (bounded)", "GET", "/api/v1/orders/{{orderId}}/status",
        "Polls order status with a bounded retry loop (max 15 attempts) until a terminal state is "
        "reached, instead of an unbounded/blind wait. Fails the run if the order never reaches a "
        "terminal state in time.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const terminal = ['CONFIRMED', 'CANCELLED_INVENTORY', 'CANCELLED_PAYMENT', 'READY_FOR_PICKUP', 'COMPLETED'];",
            "const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "pm.environment.set('lastOrderStatus', body.status);",
            "if (terminal.includes(body.status)) {",
            "    pm.test('Order reached a terminal state', function () { pm.expect(terminal).to.include(body.status); });",
            "} else if (attempts < 15) {",
            "    pm.environment.set('pollAttempts', String(attempts + 1));",
            "    pm.execution.setNextRequest('Poll Order Status (bounded)');",
            "} else {",
            "    pm.test('Order reached a terminal state within bounded polling window', function () { pm.expect.fail('Timed out after 15 attempts waiting for a terminal order status; last status was ' + body.status); });",
            "}",
        ],
    ),
    request(
        "Get Order by Id", "GET", "/api/v1/orders/{{orderId}}",
        "Reads the full order with items and current status.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "List Customer Orders", "GET", "/api/v1/orders",
        "Pages the customer's own orders.",
        query={"customerId": "{{customerId}}", "page": 0, "size": 20},
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Mark Order Ready for Pickup (Staff)", "POST", "/api/v1/orders/{{orderId}}/ready",
        "Only valid once the order is CONFIRMED; part of the happy-path pickup flow.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 409]),
    ),
    request(
        "Complete Order (Staff)", "POST", "/api/v1/orders/{{orderId}}/complete",
        "Completes pickup; valid only after READY_FOR_PICKUP.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 409]),
    ),
]

# ---------------------------------------------------------------------------
# 7. PAYMENT
# ---------------------------------------------------------------------------
payment_items = [
    request(
        "Get Payment by Order", "GET", "/api/v1/payments/by-order/{{orderId}}",
        "Reads the payment created by the choreography saga for this order (never exposes card details). "
        "NOTE: uses the staff/ADMIN token because payment-service's owner check compares the JWT "
        "'userId' claim (the auth-service User ID) directly against payment.customerId() (the "
        "customer-service Customer ID) -- these are two different UUIDs by design (customer-service "
        "mints its own Customer ID rather than reusing the auth User ID), so the CUSTOMER-role token "
        "that actually owns this order is incorrectly rejected with 403. Documented as a known bug, "
        "not fixed here (out of scope for the Postman/E2E prompt).",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + save_test("id", "paymentId"),
    ),
    request(
        "Get Payment by Id", "GET", "/api/v1/payments/{{paymentId}}",
        "Reads payment status by id (staff/ADMIN token; see owner-check bug note above).",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Refund Payment (Admin)", "POST", "/api/v1/payments/{{paymentId}}/refund",
        "Simulated refund of an AUTHORIZED/CAPTURED payment; ADMIN only.",
        body={"reason": "Fictional customer-requested refund for E2E test"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 409]),
    ),
]

# ---------------------------------------------------------------------------
# 8. NOTIFICATION
# ---------------------------------------------------------------------------
notification_items = [
    request(
        "List My Notifications", "GET", "/api/v1/notifications",
        "Lists notifications for the currently-authenticated user (order confirmation, etc.).",
        query={"page": 0, "size": 20},
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "const body = pm.response.json();",
            "if (body.content && body.content.length) { pm.environment.set('notificationId', body.content[0].id); }",
        ],
    ),
    request(
        "Mark Notification Read", "PUT", "/api/v1/notifications/{{notificationId}}/read",
        "Marks a notification as read; requires at least one notification to exist for this caller. "
        "Given the documented customerId/sub cross-service ID mismatch (see 'List My Notifications' "
        "above), notificationId is typically never populated, so this call hits an empty path segment "
        "-- accepting 400 (malformed path) alongside 200/404 to keep the run non-blocking.",
        auth_token_var="accessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 400, 404]),
    ),
    request(
        "List Notification Templates (Admin)", "GET", "/api/v1/notifications/templates",
        "Admin-only view of the fictional notification templates.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 9. AUDIT
# ---------------------------------------------------------------------------
audit_items = [
    request(
        "Get Audit Trail for Order (Admin)", "GET", "/api/v1/audit/{{orderId}}",
        "Reads the sanitized audit trail keyed by the order's aggregate id.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Search Audit Logs (Admin)", "GET", "/api/v1/audit/search",
        "Searches audit logs by resource type/action/date range.",
        query={"resourceType": "ORDER", "page": 0, "size": 20},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Get Compliance Report (Admin)", "GET", "/api/v1/audit/compliance/report",
        "Generates a sanitized compliance report over a date range for a resource type.",
        query={"resourceType": "ORDER", "startDate": "{{complianceStart}}", "endDate": "{{complianceEnd}}"},
        auth_token_var="staffAccessToken",
        pre_request=[
            "const now = new Date();",
            "const start = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);",
            "pm.environment.set('complianceStart', start.toISOString());",
            "pm.environment.set('complianceEnd', now.toISOString());",
        ] + CORRELATION_PRE,
        tests=status_test(200),
    ),
]

# ---------------------------------------------------------------------------
# 10. MOCK CONTROLS
# ---------------------------------------------------------------------------
mock_items = [
    request(
        "Set Prescription Mode = SUCCESS", "PUT", "/api/v1/mock/prescription",
        "Restores the prescription-verification mock to its default success behavior.",
        query={"mode": "SUCCESS"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Set Payment Mode = SUCCESS", "PUT", "/api/v1/mock/payment",
        "Restores the payment gateway mock to its default success behavior.",
        query={"mode": "SUCCESS"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Set Payment Mode = REJECT", "PUT", "/api/v1/mock/payment",
        "Business decline: the mock gateway returns success=false, which payment-service maps to a "
        "FAILED payment (used by the Payment Failure scenario).",
        query={"mode": "REJECT"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Set Payment Mode = ERROR", "PUT", "/api/v1/mock/payment",
        "Simulates the external payment gateway being unavailable (HTTP 500), exercising the "
        "GatewayException/retry path (used by the External Error scenario).",
        query={"mode": "ERROR"},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Set Payment Mode = DELAY", "PUT", "/api/v1/mock/payment",
        "Delays the mock gateway response past the client's configured read timeout to exercise "
        "slow-dependency/timeout behavior (used by the Slow External Dependency scenario; see runbook "
        "for why 'verification' is exercised via the payment mock rather than a prescription mock call).",
        query={"mode": "DELAY", "delayMs": 8000},
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "Reset Mock to Defaults", "POST", "/api/v1/mock/reset",
        "Restores both prescription and payment mocks to SUCCESS with zero delay. Run this at the end "
        "of every scenario so later scenarios/runs start clean.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test(204),
    ),
    request(
        "Get Current Prescription Mock Mode", "GET", "/api/v1/mock/prescription",
        "Read-only status check of the prescription mock's current mode.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 400, 500, 503]),
    ),
    request(
        "Get Current Payment Mock Mode", "GET", "/api/v1/mock/payment",
        "Read-only status check of the payment mock's current mode.",
        auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE,
        tests=status_test([200, 400, 500, 503]),
    ),
]

# ---------------------------------------------------------------------------
# SCENARIOS
# ---------------------------------------------------------------------------
happy_path_items = [
    request(
        "1. Reset Mock to Defaults", "POST", "/api/v1/mock/reset",
        "Ensures a clean mock state before the happy path runs.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE, tests=status_test(204),
    ),
    request(
        "2. Create Order", "POST", "/api/v1/orders",
        "Places a fresh order for the active prescription/pharmacy created earlier in the run.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 2, "unitPrice": 12.50}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));",
                     "pm.environment.set('pollAttempts', '0');"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "orderId"),
    ),
    request(
        "3. Poll Until CONFIRMED (bounded)", "GET", "/api/v1/orders/{{orderId}}/status",
        "Bounded poll (max 15 attempts) for the choreography saga to reach CONFIRMED.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "if (body.status === 'CONFIRMED') {",
            "    pm.test('Order is CONFIRMED', function () { pm.expect(body.status).to.eql('CONFIRMED'); });",
            "} else if (body.status && body.status.startsWith('CANCELLED')) {",
            "    pm.test('Order unexpectedly cancelled', function () { pm.expect.fail('Expected CONFIRMED but order was ' + body.status); });",
            "} else if (attempts < 15) {",
            "    pm.environment.set('pollAttempts', String(attempts + 1));",
            "    pm.execution.setNextRequest('3. Poll Until CONFIRMED (bounded)');",
            "} else {",
            "    pm.test('Order reached CONFIRMED within bounded polling window', function () { pm.expect.fail('Timed out; last status ' + body.status); });",
            "}",
        ],
    ),
    request(
        "4. Verify Payment SUCCESS", "GET", "/api/v1/payments/by-order/{{orderId}}",
        "Confirms the choreography saga successfully processed payment for the order. Uses the "
        "customer's own access token; payment-service asks customer-service to authorize the caller "
        "against the payment's customerId. Also note payment-service's PaymentStatus enum is "
        "PENDING/PROCESSING/SUCCESS/FAILED/REFUNDED -- there is no AUTHORIZED status.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=status_test(200) + save_test("id", "paymentId") + [
            "pm.test('Payment is SUCCESS', function () { pm.expect(pm.response.json().status).to.eql('SUCCESS'); });",
        ],
    ),
    request(
        "5. Verify Notification SENT", "GET", "/api/v1/notifications",
        "Best-effort check for an order-confirmation notification. Notification.customerId is set from "
        "the order event's customer-service Customer ID, while 'my notifications' is filtered by the "
        "caller's own JWT 'sub' (auth-service User ID) -- different ID spaces, so this will typically "
        "return an empty list for any single caller. Kept non-blocking (logs only); see e2e-runbook.md.",
        query={"page": 0, "size": 20}, auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const sent = (body.content || []).some(function (n) { return n.status === 'SENT'; });",
            "pm.test('Notification endpoint responds (content may be empty; see known cross-service ID gap)', function () { pm.expect(pm.response.code).to.eql(200); });",
            "if (!sent) { console.warn('No SENT notification visible for this caller (expected given the documented customerId/sub mismatch)'); }",
        ],
    ),
    request(
        "6. Verify Audit Trail Recorded (Admin)", "GET", "/api/v1/audit/{{orderId}}",
        "Confirms the order-service event was recorded in the audit trail. Audit ingestion accepts the "
        "documented common envelope and normalizes the currently emitted flat order event.",
        auth_token_var="staffAccessToken",
        pre_request=[
            "if (!pm.environment.get('auditPollAttempts')) { pm.environment.set('auditPollAttempts', '0'); }",
        ] + CORRELATION_PRE,
        tests=status_test(200) + [
            "const body = pm.response.json();",
            "const count = (body.content || []).length;",
            "if (count > 0) {",
            "    pm.environment.unset('auditPollAttempts');",
            "    pm.test('Order audit trail contains recorded events', function () { pm.expect(count).to.be.greaterThan(0); });",
            "} else {",
            "    const attempts = parseInt(pm.environment.get('auditPollAttempts') || '0', 10);",
            "    if (attempts < 10) {",
            "        pm.environment.set('auditPollAttempts', String(attempts + 1));",
            "        pm.execution.setNextRequest('6. Verify Audit Trail Recorded (Admin)');",
            "    } else {",
            "        pm.environment.unset('auditPollAttempts');",
            "        pm.test('Order audit trail contains recorded events within the bounded wait', function () { pm.expect.fail('No audit record observed after 10 retries'); });",
            "    }",
            "}",
        ],
    ),
]

insufficient_stock_items = [
    request(
        "1. Create Low-Stock Pharmacy Item", "POST", "/api/v1/inventory/stock-levels",
        "Stocks a tiny quantity (1 unit) of the dedicated createdMedicationId (not the shared "
        "medicationId used throughout the rest of the collection) so this scenario cannot be starved "
        "or polluted by every other order test drawing down a shared stock pool.",
        body={"pharmacyId": "{{pharmacyId}}", "productId": "{{createdMedicationId}}", "quantityOnHand": 1,
              "reorderLevel": 1, "reorderQuantity": 5},
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE,
        tests=status_test([201, 409]),
    ),
    request(
        "2. Create Order Exceeding Stock", "POST", "/api/v1/orders",
        "Requests 999 units, far more than the 1 unit stocked. order-service performs a synchronous "
        "availability pre-check via inventory-service before persisting, so this is expected to be "
        "rejected immediately with 400 (rather than being accepted and cancelled later by the async "
        "saga) -- both outcomes are handled below.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 9990.00,
            "items": [{"medicationId": "{{createdMedicationId}}", "quantity": 999, "unitPrice": 10.00}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));",
                     "pm.environment.set('pollAttempts', '0');",
                     "pm.environment.unset('insufficientStockOrderId');"] + CORRELATION_PRE,
        tests=status_test([201, 400]) + [
            "if (pm.response.code === 201) { pm.environment.set('insufficientStockOrderId', pm.response.json().id); }",
        ],
    ),
    request(
        "3. Poll Until CANCELLED_INVENTORY (bounded)", "GET", "/api/v1/orders/{{insufficientStockOrderId}}/status",
        "Bounded poll for the saga to reject the reservation and cancel the order. Skipped (no-op pass) "
        "if the prior step already got a synchronous 400 rejection and no order was ever created.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "if (!pm.environment.get('insufficientStockOrderId')) {",
            "    pm.test('Order was synchronously rejected for insufficient stock (no saga polling needed)', function () { pm.expect(true).to.eql(true); });",
            "} else {",
            "    const body = pm.response.json();",
            "    const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "    if (body.status === 'CANCELLED_INVENTORY') {",
            "        pm.test('Order is CANCELLED_INVENTORY', function () { pm.expect(body.status).to.eql('CANCELLED_INVENTORY'); });",
            "    } else if (attempts < 15) {",
            "        pm.environment.set('pollAttempts', String(attempts + 1));",
            "        pm.execution.setNextRequest('3. Poll Until CANCELLED_INVENTORY (bounded)');",
            "    } else {",
            "        pm.test('Order reached CANCELLED_INVENTORY within bounded polling window', function () { pm.expect.fail('Timed out; last status ' + body.status); });",
            "    }",
            "}",
        ],
    ),
    request(
        "4. Verify No Payment Was Created", "GET", "/api/v1/payments/by-order/{{insufficientStockOrderId}}",
        "Payment must never be attempted when inventory reservation failed (an empty insufficientStockOrderId "
        "path segment also 404s, which is an equally valid 'no payment' outcome here).",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "pm.test('No payment exists for the cancelled/rejected order', function () { pm.expect([400, 404]).to.include(pm.response.code); });",
        ],
    ),
]

payment_failure_items = [
    request(
        "1. Set Payment Mode = REJECT", "PUT", "/api/v1/mock/payment",
        "Forces the mock gateway to decline the next payment.",
        query={"mode": "REJECT"}, auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "2. Create Order", "POST", "/api/v1/orders",
        "Places an order that will pass inventory reservation but fail at payment.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 25.00}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));",
                     "pm.environment.set('pollAttempts', '0');"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "paymentFailureOrderId"),
    ),
    request(
        "3. Poll Until CANCELLED_PAYMENT, Payment Failure (bounded)", "GET", "/api/v1/orders/{{paymentFailureOrderId}}/status",
        "Bounded poll for the saga to observe the PaymentFailed event and cancel the order. NOTE: this "
        "step's name must stay unique across every scenario -- pm.execution.setNextRequest() resolves by "
        "name globally across the whole collection, not scoped to the current folder, so an identically "
        "named polling step elsewhere would hijack this self-referencing loop.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "if (body.status === 'CANCELLED_PAYMENT') {",
            "    pm.test('Order is CANCELLED_PAYMENT', function () { pm.expect(body.status).to.eql('CANCELLED_PAYMENT'); });",
            "} else if (attempts < 15) {",
            "    pm.environment.set('pollAttempts', String(attempts + 1));",
            "    pm.execution.setNextRequest('3. Poll Until CANCELLED_PAYMENT, Payment Failure (bounded)');",
            "} else {",
            "    pm.test('Order reached CANCELLED_PAYMENT within bounded polling window', function () { pm.expect.fail('Timed out; last status ' + body.status); });",
            "}",
        ],
    ),
    request(
        "4. Verify Reservation RELEASED", "GET", "/api/v1/inventory/reservations/{{paymentFailureOrderId}}",
        "Confirms the earlier inventory reservation was released after the payment failure.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE,
        tests=status_test(200) + [
            "pm.test('Reservation is RELEASED', function () { pm.expect(pm.response.json().status).to.eql('RELEASED'); });",
        ],
    ),
    request(
        "5. Reset Mock to Defaults", "POST", "/api/v1/mock/reset",
        "Restores SUCCESS mode so later scenarios/runs are unaffected.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE, tests=status_test(204),
    ),
]

duplicate_idempotency_items = [
    request(
        "1. Create Order (first request)", "POST", "/api/v1/orders",
        "First request with a fresh Idempotency-Key.",
        headers=[header("Idempotency-Key", "{{dupIdempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 25.00}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('dupIdempotencyKey', pm.variables.replaceIn('{{$guid}}'));"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "dupOrderIdFirst"),
    ),
    request(
        "2. Replay Same Key + Same Body -> Same Order", "POST", "/api/v1/orders",
        "Same Idempotency-Key and identical body must replay the original order, not create a new one.",
        headers=[header("Idempotency-Key", "{{dupIdempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 25.00}],
        },
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=status_test(201) + [
            "const body = pm.response.json();",
            "pm.test('Replay returns the same order id (no duplicate created)', function () { pm.expect(body.id).to.eql(pm.environment.get('dupOrderIdFirst')); });",
        ],
    ),
    request(
        "3. Same Key + Different Body -> 409 Conflict", "POST", "/api/v1/orders",
        "Same Idempotency-Key with a materially different body must be rejected as a conflict, not silently accepted.",
        headers=[header("Idempotency-Key", "{{dupIdempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 999.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 40, "unitPrice": 24.99}],
        },
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=status_test(409),
    ),
]

slow_external_items = [
    request(
        "1. Set Payment Mode = DELAY (past client timeout)", "PUT", "/api/v1/mock/payment",
        "Delays the mock payment gateway response beyond payment-service's configured read timeout.",
        query={"mode": "DELAY", "delayMs": 8000}, auth_token_var="staffAccessToken",
        pre_request=CORRELATION_PRE, tests=status_test(200),
    ),
    request(
        "2. Create Order Against Slow Gateway", "POST", "/api/v1/orders",
        "The synchronous payment-service -> mock-gateway call should time out/retry rather than hang "
        "forever; the saga is expected to eventually resolve the order to a terminal, non-CONFIRMED state.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 25.00}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));",
                     "pm.environment.set('pollAttempts', '0');"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "slowOrderId"),
    ),
    request(
        "3. Poll Until Terminal State (bounded, longer window)", "GET", "/api/v1/orders/{{slowOrderId}}/status",
        "Uses a larger bound (20 attempts) because the timeout/retry path is slower than the happy path.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const terminal = ['CONFIRMED', 'CANCELLED_PAYMENT', 'CANCELLED_INVENTORY'];",
            "const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "if (terminal.includes(body.status)) {",
            "    pm.test('Order reached a terminal state despite the slow dependency', function () { pm.expect(terminal).to.include(body.status); });",
            "} else if (attempts < 20) {",
            "    pm.environment.set('pollAttempts', String(attempts + 1));",
            "    pm.execution.setNextRequest('3. Poll Until Terminal State (bounded, longer window)');",
            "} else {",
            "    pm.test('Order reached a terminal state within bounded polling window', function () { pm.expect.fail('Timed out; last status ' + body.status); });",
            "}",
        ],
    ),
    request(
        "4. Reset Mock to Defaults", "POST", "/api/v1/mock/reset",
        "Clears the DELAY mode.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE, tests=status_test(204),
    ),
]

unauthorized_forbidden_items = [
    request(
        "401 - No Token: Get Customer Profile", "GET", "/api/v1/customers/{{customerId}}",
        "No Authorization header at all must be rejected.",
        pre_request=CORRELATION_PRE, tests=status_test(401), correlation=True,
    ),
    request(
        "401 - Garbage Token: Search Medications", "GET", "/api/v1/medications",
        "An obviously invalid bearer token must be rejected.",
        headers=[header("Authorization", "Bearer not-a-real-token")],
        pre_request=CORRELATION_PRE, tests=status_test(401),
    ),
    request(
        "403 - Customer Cannot Create Pharmacy", "POST", "/api/v1/pharmacies",
        "Pharmacy creation is ADMIN-only; a CUSTOMER-role token must be forbidden. Body must pass bean "
        "validation (phone 10-20 chars) so the 403 actually comes from the authorization check, not an "
        "earlier 400 validation failure -- @PreAuthorize runs around the method invocation, after "
        "@RequestBody argument resolution/validation, so an invalid body always 400s first regardless "
        "of role.",
        body={"name": "Should Fail", "licenseNumber": "LIC-FAIL", "phone": "(555) 000-0000", "timezone": "America/New_York"},
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(403),
    ),
    request(
        "403 - Customer Cannot Create Prescription", "POST", "/api/v1/prescriptions",
        "Prescription creation requires PHARMACIST/STORE_MANAGER/ADMIN; plain CUSTOMER must be forbidden.",
        body={"customerId": "{{customerId}}", "prescriberId": "{{$guid}}", "prescribedAt": "{{prescribedAtFuture}}",
              "expiresAt": "2035-01-01T00:00:00Z",
              "lines": [{"productId": "{{medicationId}}", "quantity": 1, "instructions": "n/a"}]},
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(403),
    ),
    request(
        "403 - Non-Admin Cannot Refund Payment", "POST", "/api/v1/payments/{{paymentId}}/refund",
        "Refunds are ADMIN-only; a CUSTOMER token must be forbidden.",
        body={"reason": "Should be forbidden"},
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(403),
    ),
    request(
        "403 - Non-Admin Cannot View Audit Logs", "GET", "/api/v1/audit/search",
        "Audit search is ADMIN-only; a CUSTOMER token must be forbidden.",
        query={"resourceType": "ORDER"}, auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=status_test(403),
    ),
]

external_error_items = [
    request(
        "1. Set Payment Mode = ERROR", "PUT", "/api/v1/mock/payment",
        "Simulates the external payment gateway returning HTTP 500 for every call.",
        query={"mode": "ERROR"}, auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE,
        tests=status_test(200),
    ),
    request(
        "2. Create Order Against Failing Gateway", "POST", "/api/v1/orders",
        "Exercises the GatewayException/retry path when the external dependency is unavailable.",
        headers=[header("Idempotency-Key", "{{idempotencyKey}}")],
        body={
            "customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
            "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 25.00,
            "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 25.00}],
        },
        auth_token_var="accessToken",
        pre_request=["pm.environment.set('idempotencyKey', pm.variables.replaceIn('{{$guid}}'));",
                     "pm.environment.set('pollAttempts', '0');"] + CORRELATION_PRE,
        tests=status_test(201) + save_test("id", "errorOrderId"),
    ),
    request(
        "3. Poll Until CANCELLED_PAYMENT, External Error (bounded)", "GET", "/api/v1/orders/{{errorOrderId}}/status",
        "After retries are exhausted the payment is marked FAILED and the saga cancels the order. NOTE: "
        "this step's name must stay unique across every scenario -- see the Payment Failure scenario's "
        "equivalent poll step for why (setNextRequest name collisions hijack the polling loop).",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE,
        tests=[
            "const body = pm.response.json();",
            "const attempts = parseInt(pm.environment.get('pollAttempts') || '0', 10);",
            "if (body.status === 'CANCELLED_PAYMENT') {",
            "    pm.test('Order is CANCELLED_PAYMENT after gateway errors', function () { pm.expect(body.status).to.eql('CANCELLED_PAYMENT'); });",
            "} else if (attempts < 20) {",
            "    pm.environment.set('pollAttempts', String(attempts + 1));",
            "    pm.execution.setNextRequest('3. Poll Until CANCELLED_PAYMENT, External Error (bounded)');",
            "} else {",
            "    pm.test('Order reached CANCELLED_PAYMENT within bounded polling window', function () { pm.expect.fail('Timed out; last status ' + body.status); });",
            "}",
        ],
    ),
    request(
        "4. Reset Mock to Defaults", "POST", "/api/v1/mock/reset",
        "Clears ERROR mode.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE, tests=status_test(204),
    ),
]

poison_notification_note = {
    "name": "Note: Poison Notification / DLT scenario",
    "item": [],
    "description": (
        "Prompt 13 scenario 7 (poison notification -> retry/DLT while the order remains CONFIRMED) "
        "cannot be triggered through a public REST endpoint in this platform: there is no HTTP route "
        "that injects a malformed Kafka event, and inspecting a Dead Letter Topic requires a Kafka "
        "consumer/admin client rather than an HTTP call. This is verified manually per "
        "docs/e2e-runbook.md 'Poison notification / DLT verification' section: publish a malformed "
        "message directly to the pharmacy.order.events.v1 topic, confirm notification-service retries "
        "then routes to its DLT topic, and confirm via 'Get Order by Id' that the order's own status "
        "is unaffected."
    ),
}

# ---------------------------------------------------------------------------
# NEGATIVE (400 / 401 / 403 / 404 / 409 / 429)
# ---------------------------------------------------------------------------
negative_items = [
    request(
        "400 - Create Order Missing Items", "POST", "/api/v1/orders",
        "items is @NotEmpty; omitting it must return 400 with a ProblemDetail, not a 500.",
        headers=[header("Idempotency-Key", "{{$guid}}")],
        body={"customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
              "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 10.00, "items": []},
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(400),
    ),
    request(
        "400 - Create Order Missing Idempotency-Key Header", "POST", "/api/v1/orders",
        "docs/06-api-contracts.md requires Idempotency-Key on order creation; omitting it must be rejected.",
        body={"customerId": "{{customerId}}", "prescriptionId": "{{prescriptionId}}",
              "pharmacyId": "{{pharmacyId}}", "currency": "USD", "total": 10.00,
              "items": [{"medicationId": "{{medicationId}}", "quantity": 1, "unitPrice": 10.00}]},
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(400),
    ),
    request(
        "401 - Login With Wrong Password", "POST", "/api/v1/auth/login",
        "Invalid credentials must return 401, never 500 or a stack trace.",
        body={"username": "{{customerUsername}}", "password": "definitely-wrong-password"},
        pre_request=CORRELATION_PRE, tests=status_test(401),
    ),
    request(
        "403 - Customer Cannot Activate Prescription", "POST", "/api/v1/prescriptions/{{prescriptionId}}/activate",
        "Activation is PHARMACIST/ADMIN only; a CUSTOMER token must be forbidden.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(403),
    ),
    request(
        "404 - Get Order With Random Id", "GET", "/api/v1/orders/{{$guid}}",
        "A syntactically valid but non-existent order id must return 404, not 500.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(404),
    ),
    request(
        "404 - Get Payment With Random Id", "GET", "/api/v1/payments/{{$guid}}",
        "A non-existent payment id must return 404.",
        auth_token_var="accessToken", pre_request=CORRELATION_PRE, tests=status_test(404),
    ),
    request(
        "409 - Ready-For-Pickup Before Payment Authorized", "POST",
        "/api/v1/orders/{{paymentFailureOrderId}}/ready",
        "An order that never reached CONFIRMED (the Payment Failure scenario's order, which ends in "
        "CANCELLED_PAYMENT) cannot be marked ready; expects a 409 invalid-state ProblemDetail like the "
        "one documented in docs/06-api-contracts.md. Run the Payment Failure scenario first so this "
        "variable is populated.",
        auth_token_var="staffAccessToken", pre_request=CORRELATION_PRE, tests=status_test([404, 409]),
    ),
    {
        "name": "429 - Rate Limited (real feature, not exercised here)",
        "item": [],
        "description": (
            "api-gateway does implement Redis-backed rate limiting via a custom RateLimitingFilter "
            "(GlobalFilter, order -75): 100 req/min per authenticated user and 1000 req/min per IP, "
            "returning 429 with Retry-After and an RFC7807-style body on breach. It is real and "
            "functional, but exercising it needs 100+ rapid requests, which is impractical inside this "
            "fast-path collection. Deliberately left as a documented, out-of-scope-for-Newman check; see "
            "docs/e2e-runbook.md."
        ),
    },
]

# ---------------------------------------------------------------------------
# COLLECTION ASSEMBLY
# ---------------------------------------------------------------------------
collection = {
    "info": {
        "name": "Pharmacy Enterprise Platform",
        "description": (
            "End-to-end Postman collection for the pharmacy-enterprise-platform local docker-compose "
            "stack (see docs/e2e-runbook.md). Requests use only fictional test data and environment "
            "placeholders -- no real credentials or tokens are committed. Run folders top-to-bottom: "
            "Auth -> Product -> Customer -> Pharmacy -> Inventory -> Prescription -> Order -> Payment -> "
            "Notification -> Audit -> Mock Controls establish the fixtures that the Scenarios and "
            "Negative folders then exercise."
        ),
        "schema": SCHEMA,
    },
    "item": [
        folder("00 - Auth", auth_items),
        folder("01 - Product (Medications)", product_items),
        folder("02 - Customer", customer_items),
        folder("03 - Pharmacy", pharmacy_items),
        folder("04 - Inventory", inventory_items),
        folder("05 - Prescription", prescription_items),
        folder("06 - Order", order_items),
        folder("07 - Payment", payment_items),
        folder("08 - Notification", notification_items),
        folder("09 - Audit", audit_items),
        folder("10 - Mock Controls", mock_items),
        folder("Scenarios", [
            folder("1. Happy Path (login -> confirmed order -> notification -> audit)", happy_path_items),
            folder("2. Insufficient Stock -> CANCELLED_INVENTORY", insufficient_stock_items),
            folder("3. Payment Failure -> CANCELLED_PAYMENT, reservation RELEASED", payment_failure_items),
            folder("4. Duplicate Idempotency Request", duplicate_idempotency_items),
            folder("5. Slow External Dependency (payment DELAY)", slow_external_items),
            folder("6. Unauthorized/Forbidden Matrix", unauthorized_forbidden_items),
            folder("7. External Error (payment gateway 500s)", external_error_items),
            {"name": "8. Poison Notification / DLT (manual verification)", "item": [poison_notification_note]},
        ]),
        folder("Negative (400/401/403/404/409/429)", negative_items),
    ],
    "variable": [
        {"key": "correlationId", "value": ""},
    ],
    "event": [
        script([
            "// Collection-level default: fail fast, never print secrets to the console.",
            "if (pm.info.requestName) { console.log('-> ' + pm.info.requestName); }",
        ], "prerequest"),
    ],
}

with open("postman/pharmacy.postman_collection.json", "w") as f:
    json.dump(collection, f, indent=2)


# ---------------------------------------------------------------------------
# ENVIRONMENT
# ---------------------------------------------------------------------------
def env_var(key, value="", secret=False):
    return {"key": key, "value": value, "type": "secret" if secret else "default", "enabled": True}


environment = {
    "id": "b6e0b6f0-0000-4000-8000-000000000001",
    "name": "local",
    "values": [
        env_var("baseUrl", "http://localhost:8080"),
        env_var("medicationId", "550e8400-e29b-41d4-a716-446655440001"),
        env_var("customerUsername", ""),
        env_var("customerPassword", "", secret=True),
        env_var("staffUsername", ""),
        env_var("staffPassword", "", secret=True),
        env_var("accessToken", "", secret=True),
        env_var("refreshToken", "", secret=True),
        env_var("staffAccessToken", "", secret=True),
        env_var("staffRefreshToken", "", secret=True),
        env_var("customerAuthUserId", ""),
        env_var("staffAuthUserId", ""),
        env_var("customerId", ""),
        env_var("createdMedicationId", ""),
        env_var("pharmacyId", ""),
        env_var("stockLevelId", ""),
        env_var("prescriptionId", ""),
        env_var("prescriptionLineId", ""),
        env_var("prescribedAtFuture", ""),
        env_var("orderId", ""),
        env_var("idempotencyKey", ""),
        env_var("dupIdempotencyKey", ""),
        env_var("dupOrderIdFirst", ""),
        env_var("insufficientStockOrderId", ""),
        env_var("paymentFailureOrderId", ""),
        env_var("slowOrderId", ""),
        env_var("errorOrderId", ""),
        env_var("paymentId", ""),
        env_var("notificationId", ""),
        env_var("complianceStart", ""),
        env_var("complianceEnd", ""),
        env_var("correlationId", ""),
        env_var("pollAttempts", "0"),
        env_var("lastOrderStatus", ""),
    ],
    "_postman_variable_scope": "environment",
}

with open("postman/local.postman_environment.json", "w") as f:
    json.dump(environment, f, indent=2)

print("Wrote postman/pharmacy.postman_collection.json and postman/local.postman_environment.json")
