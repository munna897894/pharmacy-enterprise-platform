# Audit Service

A comprehensive audit logging service for the pharmacy-enterprise-platform that tracks all resource modifications across services and provides compliance reporting capabilities.

## Overview

The Audit Service consumes events from all microservices via Kafka and persists comprehensive audit logs to a MySQL database. It provides REST endpoints for audit trail retrieval, advanced search, and compliance reporting with admin-only access.

## Features

- **Event-Driven Architecture**: Consumes events from all services (product, customer, order, payment, prescription, inventory)
- **Idempotent Processing**: Duplicate event handling via `ProcessedEvent` tracking
- **Comprehensive Logging**: Tracks CREATE, READ, UPDATE, DELETE, EXPORT, LOGIN, LOGOUT, FAILED_LOGIN actions
- **Search & Filtering**: Advanced search by resource type, action, user, service, and date range
- **Compliance Reports**: Generate audit reports by resource type with action counts and affected users
- **Performance Optimized**: Composite indexes on (resource_type, timestamp, action) and (aggregate_id, timestamp)
- **Admin-Only Access**: All endpoints require ADMIN role
- **Caching**: Compliance reports cached to improve query performance
- **Data Integrity**: Immutable audit logs (no deletes), 7-year retention policy

## Database Schema

### audit_logs Table
```sql
CREATE TABLE audit_logs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,         -- Resource being audited
    service VARCHAR(50) NOT NULL,              -- Source service
    action VARCHAR(20) NOT NULL,               -- ENUM: CREATE, UPDATE, DELETE, etc.
    resource_type VARCHAR(30) NOT NULL,        -- ENUM: PRODUCT, ORDER, PAYMENT, etc.
    user_id VARCHAR(36),                       -- User who performed the action
    username VARCHAR(100),                     -- Username for display
    timestamp TIMESTAMP(6) NOT NULL,           -- Action occurrence time (UTC)
    status VARCHAR(20) NOT NULL,               -- ENUM: SUCCESS, FAILURE
    details JSON,                              -- Delta/change information
    ip_address VARCHAR(50),                    -- Client IP address
    created_at TIMESTAMP(6) NOT NULL           -- Record creation time
);

-- Composite indexes for query performance
CREATE INDEX idx_aggregate_id_timestamp ON audit_logs(aggregate_id, timestamp);
CREATE INDEX idx_user_id_timestamp ON audit_logs(user_id, timestamp);
CREATE INDEX idx_resource_type_timestamp_action ON audit_logs(resource_type, timestamp, action);
```

### processed_events Table
```sql
CREATE TABLE processed_events (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
```

## REST API Endpoints

### 1. Get Audit Trail for Resource
```
GET /api/v1/audit/{aggregateId}?page=0&size=20
```
Returns all audit log entries for a specific resource ID in chronological order.

**Response**: Paginated list of AuditLogResponse
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "aggregateId": "123e4567-e89b-12d3-a456-426614174000",
      "service": "product-service",
      "action": "UPDATE",
      "resourceType": "PRODUCT",
      "userId": "user-123",
      "username": "john_admin",
      "timestamp": "2024-09-15T10:30:00Z",
      "status": "SUCCESS",
      "details": "{\"quantity\":[\"1000\",\"950\"]}",
      "ipAddress": "192.168.1.100"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 20},
  "totalElements": 150,
  "totalPages": 8
}
```

### 2. Search Audit Logs
```
GET /api/v1/audit/search?resourceType=ORDER&action=CREATE&userId=user-123&startDate=2024-01-01T00:00:00Z&endDate=2024-12-31T23:59:59Z&page=0&size=50
```
Advanced search with multiple filter criteria.

**Query Parameters**:
- `resourceType` (optional): PRODUCT, CUSTOMER, ORDER, PAYMENT, PRESCRIPTION, INVENTORY, PHARMACY, USER, SETTINGS
- `action` (optional): CREATE, READ, UPDATE, DELETE, EXPORT, LOGIN, LOGOUT, FAILED_LOGIN
- `userId` (optional): UUID of the user
- `service` (optional): Service name (e.g., "product-service")
- `startDate` (optional): ISO-8601 format, defaults to 90 days ago
- `endDate` (optional): ISO-8601 format, defaults to now
- `page`: Pagination page number (default: 0)
- `size`: Page size (default: 20)

### 3. Get Compliance Report
```
GET /api/v1/audit/compliance/report?resourceType=PRODUCT&startDate=2024-01-01T00:00:00Z&endDate=2024-12-31T23:59:59Z
```
Generate audit report for a resource type within date range.

**Response**: ComplianceReportResponse
```json
{
  "reportPeriod": "2024-01-01T00:00:00Z to 2024-12-31T23:59:59Z",
  "resourceType": "PRODUCT",
  "totalActions": 156,
  "actionCounts": {
    "CREATE": 25,
    "UPDATE": 120,
    "DELETE": 11
  },
  "failureCount": 0,
  "affectedUsers": ["admin_user", "manager_user", "staff_user"],
  "generatedAt": "2024-09-16T12:23:15.694Z"
}
```

## Kafka Consumer Architecture

The service listens to events from all services via Spring Cloud Stream:

### Supported Event Topics
- `product-service.product.events`: ProductCreated, ProductUpdated, ProductDeleted
- `customer-service.customer.events`: CustomerRegistered, CustomerUpdated, CustomerAddressChanged
- `order-service.order.events`: OrderCreated, OrderCancelled, OrderFulfilled
- `payment-service.payment.events`: PaymentProcessed, PaymentRefunded
- `prescription-service.prescription.events`: PrescriptionCreated, PrescriptionActivated, PrescriptionFilled
- `inventory-service.inventory.events`: StockLevelCreated, StockAdjusted
- `pharmacy-service.pharmacy.events`: PharmacyUpdated, HoursChanged
- `auth-service.auth.events`: UserRegistered, UserRoleChanged, UserDeactivated

### Idempotency
Each consumer checks the `ProcessedEvent` table before processing:
- If eventId is already processed, event is skipped
- Otherwise, audit log is created and event marked as processed
- Ensures exactly-once semantics despite duplicate Kafka deliveries

## Configuration

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/audit_service
    username: audit_user
    password: password
  kafka:
    bootstrap-servers: localhost:9092
  cloud:
    stream:
      kafka:
        binder:
          brokers: localhost:9092
  cache:
    type: simple
```

### Environment Variables
```bash
DB_HOST=localhost
DB_PORT=3306
DB_NAME=audit_service
DB_USER=audit_user
DB_PASSWORD=password
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
JWT_ISSUER_URI=http://localhost:8081
JWT_JWK_SET_URI=http://localhost:8081/.well-known/jwks.json
```

## Query Performance

All endpoints meet SLA requirements with proper indexing:

| Query Type | Index | Target Time | Notes |
|-----------|-------|------------|-------|
| Audit Trail by aggregate_id | (aggregate_id, timestamp) | <100ms for 1 year | Composite index |
| User Activity History | (user_id, timestamp) | <500ms for 1 year | 10k events |
| Compliance Report | (resource_type, timestamp, action) | <2s for 1 year | Partition ready |

## Security & Authorization

- **Authentication**: Spring Security OAuth2 Resource Server with JWT
- **Authorization**: `@Secured("ROLE_ADMIN")` on all endpoints
- **JWT Validation**: RSA-signed tokens from auth-service
- **No PII Logging**: Details JSON sanitized to exclude customer PII and payment details

## Testing

Run all tests:
```bash
mvn -pl services/audit-service test
```

Test coverage includes:
- Domain model tests (AuditLog, ProcessedEvent creation)
- Service layer tests (search, filtering, compliance reports)
- Repository tests (queries, performance)
- Kafka consumer tests (event processing, idempotency)

Key test classes:
- `AuditLogTest`: Domain model validation
- `AuditLogServiceTest`: Service layer logic
- `AuditControllerTest`: REST endpoint behavior
- `AuditLogRepositoryTest`: Query correctness
- `AuditQueryPerformanceTest`: Performance validation with 10k+ records

## Development

### Building
```bash
mvn -pl services/audit-service clean package
```

### Running Locally
```bash
mvn -pl services/audit-service spring-boot:run
```

Service starts on port 8086. Check health:
```bash
curl http://localhost:8086/actuator/health
```

### Database Setup
```bash
# Create schema and user
mysql -u root -p << EOF
CREATE SCHEMA audit_service;
CREATE USER 'audit_user'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON audit_service.* TO 'audit_user'@'%';
FLUSH PRIVILEGES;
EOF
```

## Compliance & Data Retention

- **Retention Policy**: Audit logs retained for minimum 7 years (regulatory requirement)
- **No Automatic Purging**: Business decision to keep all audit data
- **Archive Strategy**: Old data can be archived to separate table if needed
- **Data Immutability**: Audit logs are immutable (no updates or deletes)

## API Documentation

OpenAPI/Swagger documentation available at:
```
http://localhost:8086/swagger-ui.html
http://localhost:8086/api-docs
```

## Metrics & Monitoring

Prometheus metrics exposed at:
```
http://localhost:8086/actuator/prometheus
```

Key metrics:
- `audit_service_audit_logs_created`: Counter for created audit logs
- `audit_service_search_duration_seconds`: Histogram for search query duration
- `audit_service_kafka_consumer_lag`: Kafka consumer lag

## Known Limitations & Future Improvements

1. **Kafka Consumer Beans**: Current implementation requires separate @Bean methods per service. Future: Consolidate into single dynamic consumer.
2. **Event Transformation**: Currently JSON-serialized event details. Future: Type-safe event POJOs.
3. **Export Format**: Currently supports JSON/CSV. Future: Add PDF export for regulatory reports.
4. **Real-time Streaming**: Currently pull-based queries. Future: WebSocket support for real-time audit trail.
5. **Partitioning**: Index strategy ready for table partitioning at 10M+ rows.

## Contributing

Follow the pharmacy-enterprise-platform standards:
- Java 21, Spring Boot 3.5.x
- Constructor injection only
- No Lombok
- Comprehensive tests required
- All timestamps in UTC

## License

Part of the pharmacy-enterprise-platform educational project.
