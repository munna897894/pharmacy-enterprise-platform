# Audit Service Architecture

## System Design Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Microservices                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │   Product    │  │   Customer   │  │    Order     │  ...       │
│  │   Service    │  │   Service    │  │   Service    │            │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘            │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          └──────────────────┴──────────────────┘
                             │
                    ┌────────▼─────────┐
                    │   Kafka Topic    │
                    │  (Event Stream)  │
                    └────────┬─────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
    ┌─────▼──────────┐ ┌────▼──────────┐ ┌────▼──────────┐
    │  Product Event │ │ Customer Event│ │  Order Event  │
    │   Consumer     │ │   Consumer    │ │   Consumer    │
    └─────┬──────────┘ └────┬──────────┘ └────┬──────────┘
          │                  │                  │
          └──────────────────┴──────────────────┘
                             │
                    ┌────────▼─────────────┐
                    │ Audit Service Layer  │
                    │ - Idempotency Check  │
                    │ - Audit Log Creation │
                    └────────┬─────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
    ┌─────▼────────────┐ ┌──▼──────────┐ ┌────▼──────────┐
    │  audit_logs      │ │processed_   │ │  Redis Cache  │
    │  (MySQL)         │ │  events     │ │ (Compliance)  │
    │                  │ │  (MySQL)    │ │               │
    └────┬─────────────┘ └─────────────┘ └───────────────┘
         │
    ┌────▼──────────────────────┐
    │   REST Endpoints           │
    │ - GET /api/v1/audit/{id}   │
    │ - GET /api/v1/audit/search │
    │ - GET .../compliance/report│
    └────┬───────────────────────┘
         │
    ┌────▼──────────────────┐
    │   Authenticated Client │
    │    (Admin Only)        │
    └───────────────────────┘
```

## Component Architecture

### 1. Domain Layer (`domain/`)
**Responsibility**: Core business entities and value objects

- **AuditLog**: JPA entity representing an audit log entry
  - Immutable data model
  - Indexes on (aggregate_id, timestamp), (user_id, timestamp), (resource_type, timestamp, action)
  - JSON column for flexible details/delta storage

- **ProcessedEvent**: Tracks processed Kafka events for idempotency
  - Event ID as primary key (VARCHAR 100)
  - Prevents duplicate audit log creation

- **Enums**:
  - `AuditAction`: CREATE, READ, UPDATE, DELETE, EXPORT, LOGIN, LOGOUT, FAILED_LOGIN
  - `AuditResourceType`: PRODUCT, CUSTOMER, PHARMACY, INVENTORY, PRESCRIPTION, ORDER, PAYMENT, USER, SETTINGS
  - `AuditStatus`: SUCCESS, FAILURE

### 2. Application Layer (`application/`)
**Responsibility**: Business logic and orchestration

- **AuditLogService**: Core service with methods:
  - `createAuditLog()`: Persist audit entry (internal API)
  - `getAuditTrail()`: Retrieve audit history for resource (paginated)
  - `getUserActivityHistory()`: Get user actions within date range
  - `getComplianceReport()`: Generate compliance statistics
  - `searchAuditLogs()`: Advanced search with multiple filters
  
- Features:
  - Transactional boundaries (`@Transactional`)
  - Caching for compliance reports (`@Cacheable`)
  - DTO mapping from entities to REST responses

### 3. Infrastructure Layer (`infrastructure/`)
**Responsibility**: Technical implementation details

#### Persistence (`infrastructure/persistence/`)
- **AuditLogRepository**: Spring Data JPA repository
  - Custom query methods using `@Query`
  - Paginated searches
  - Compiled queries for performance

- **ProcessedEventRepository**: Simple repository for idempotency tracking
  - Lookup by event ID
  - No pagination needed

#### Kafka (`infrastructure/kafka/`)
- **BaseEventConsumer**: Abstract base class
  - Idempotency check logic
  - Audit log creation helper
  - Processed event marking

- **Service-Specific Consumers** (extend BaseEventConsumer):
  - ProductEventConsumer
  - CustomerEventConsumer
  - OrderEventConsumer
  - PaymentEventConsumer
  - PrescriptionEventConsumer
  - InventoryEventConsumer

- Implementation pattern:
  ```
  1. Consumer receives Kafka message
  2. Parse JSON to extract metadata
  3. Check ProcessedEvent table for event ID
  4. If not processed:
     - Determine AuditAction based on event type
     - Map to AuditResourceType
     - Extract user ID and details
     - Call AuditLogService.createAuditLog()
     - Mark event as processed
  5. If already processed: skip silently (idempotent)
  ```

### 4. API Layer (`api/`)
**Responsibility**: REST endpoints and external contracts

- **AuditController**: REST endpoints
  - `GET /api/v1/audit/{aggregateId}`: Retrieve audit trail
  - `GET /api/v1/audit/search`: Advanced search
  - `GET /api/v1/audit/compliance/report`: Compliance report

- **AuditLogResponse**: REST DTO
  - Record type (immutable)
  - Flattened representation of AuditLog entity
  - @JsonProperty annotations for naming

- **ComplianceReportResponse**: REST DTO
  - Report period, resource type, totals
  - Action count breakdown
  - Affected users set
  - Generation timestamp

### 5. Configuration
- **SecurityConfiguration** (via Spring Security)
  - OAuth2 Resource Server with JWT
  - Admin-only role checks via `@Secured` annotations

- **CacheConfiguration**
  - Simple in-memory cache for compliance reports
  - TTL managed by Spring Cache

## Database Design

### Schema
```sql
audit_logs:
  PK: id (UUID)
  FK: aggregate_id (resource being audited)
  Columns: service, action, resource_type, user_id, username, 
           timestamp, status, details (JSON), ip_address, created_at
  Indexes: 
    - (aggregate_id, timestamp) - for audit trail queries
    - (user_id, timestamp) - for user activity history
    - (resource_type, timestamp, action) - for compliance reports

processed_events:
  PK: event_id (VARCHAR 100)
  Columns: processed_at
  Indexes: (processed_at) - for cleanup queries
```

### Partitioning Strategy
- Current: Single table with indexes
- At 10M+ rows: Partition by DATE(timestamp) for performance
- Supported by composite index structure

## Query Optimization

### Performance Targets
- **Audit Trail** (by aggregate_id): <100ms for 1 year
  - Uses index: (aggregate_id, timestamp DESC)
  - Typically <10ms for recent data

- **User Activity** (by user_id, date range): <500ms for 1 year
  - Uses index: (user_id, timestamp DESC)
  - With 10k events: ~50ms

- **Compliance Report** (by resource_type, date range): <2s for 1 year
  - Uses index: (resource_type, timestamp, action)
  - ~200ms with 10k+ records

### Index Strategy
```sql
-- Primary selection (most used)
CREATE INDEX idx_aggregate_id_timestamp 
  ON audit_logs(aggregate_id, timestamp DESC);

-- User queries
CREATE INDEX idx_user_id_timestamp 
  ON audit_logs(user_id, timestamp DESC);

-- Compliance & filtering
CREATE INDEX idx_resource_type_timestamp_action 
  ON audit_logs(resource_type, timestamp DESC, action);

-- Supporting indexes for common filters
CREATE INDEX idx_service ON audit_logs(service);
CREATE INDEX idx_timestamp ON audit_logs(timestamp);
```

## Security Architecture

### Authentication & Authorization
```
┌─────────────────────┐
│  API Request        │
│  + JWT Bearer Token │
└──────────┬──────────┘
           │
    ┌──────▼──────────────┐
    │ OAuth2 Resource     │
    │ Server              │
    │ (JWT Validation)    │
    └──────┬──────────────┘
           │
    ┌──────▼──────────────┐
    │ Role Check          │
    │ @Secured("ROLE_ADMIN")
    └──────┬──────────────┘
           │
    ┌──────▼──────────────┐
    │ Access Granted      │
    │ Process Request     │
    └─────────────────────┘
```

### Data Protection
- **Sensitive Data Sanitization**: Details JSON excludes PII and payment details
- **Audit Trail Immutability**: No UPDATE or DELETE on audit_logs
- **User Privacy**: User IDs and usernames logged for compliance, not full user data
- **7-Year Retention**: Legal/regulatory requirement

## Event Processing Flow

### Event Consumer Flow
```
Kafka Event
    ↓
Parse JSON to DomainEvent
    ↓
Extract: eventId, eventType, aggregateId, actorId
    ↓
Query: ProcessedEvent.findByEventId(eventId)
    ↓
    ├─ Found (already processed)
    │  └─ Skip (idempotent)
    │
    └─ Not Found (new event)
       ├─ Determine AuditAction from eventType
       ├─ Map to AuditResourceType
       ├─ Extract details from payload
       ├─ Call AuditLogService.createAuditLog()
       ├─ Save to audit_logs table
       ├─ Insert into processed_events table
       └─ Done (idempotent)
```

### Example: ProductUpdated Event
```json
{
  "eventId": "evt-123",
  "eventType": "ProductUpdated",
  "aggregateId": "prod-456",
  "actorId": "user-789",
  "timestamp": "2024-09-16T12:00:00Z",
  "payload": {
    "sku": "PROD001",
    "name": "Updated Product Name",
    "quantity": 950
  }
}
```

Becomes:
```json
{
  "id": "audit-id-uuid",
  "aggregateId": "prod-456",
  "service": "product-service",
  "action": "UPDATE",
  "resourceType": "PRODUCT",
  "userId": "user-789",
  "username": null,
  "timestamp": "2024-09-16T12:00:00Z",
  "status": "SUCCESS",
  "details": "{\"sku\":\"PROD001\",\"name\":\"Updated Product Name\",\"quantity\":950}",
  "ipAddress": null
}
```

## Testing Strategy

### Test Pyramid
```
        ▲
        │ Integration Tests (6 tests)
        │ - Full context loading
        │ - Kafka consumers + DB
        ├─────────────────────
        │ Repository Tests (9 tests)
        │ - Query validation
        │ - Performance checks
        ├─────────────────────
        │ Service Tests (10 tests)
        │ - Business logic
        │ - Mock repository
        ├─────────────────────
        │ Unit Tests (3 + 4)
        │ - Domain models
        │ - DTOs
        │ - Controllers
        ▼
```

### Test Classes
1. **AuditLogTest** (3 tests)
   - Entity creation with all fields
   - Optional fields handling
   - Status transitions

2. **AuditLogServiceTest** (10 tests)
   - CRUD operations
   - Search variants
   - Compliance report generation
   - Multiple action types

3. **AuditControllerTest** (3 tests)
   - Endpoint response mapping
   - Authorization validation
   - Error handling

4. **AuditLogRepositoryTest** (9 tests)
   - Query correctness
   - Pagination
   - Filter combinations

5. **AuditQueryPerformanceTest** (5 tests)
   - 10,000+ record datasets
   - Query time assertions
   - Index validation

## Deployment Considerations

### Environment Variables
```bash
DB_HOST=mysql.default.svc.cluster.local
DB_PORT=3306
DB_NAME=audit_service
DB_USER=audit_user
DB_PASSWORD=${DB_PASSWORD_SECRET}
KAFKA_BOOTSTRAP_SERVERS=kafka-broker-0:9092,kafka-broker-1:9092,kafka-broker-2:9092
JWT_ISSUER_URI=http://auth-service:8081
JWT_JWK_SET_URI=http://auth-service:8081/.well-known/jwks.json
SERVER_PORT=8086
```

### Database Initialization
```bash
# Create user and schema
CREATE SCHEMA audit_service;
CREATE USER 'audit_user'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON audit_service.* TO 'audit_user'@'%';
FLUSH PRIVILEGES;

# Flyway migrations run automatically on startup
```

### Kubernetes Deployment
- Service: `audit-service` (ClusterIP, port 8086)
- StatefulSet: 1 replica (audit logs are read-heavy, no sharding needed)
- ReadinessProbe: `/actuator/health`
- Resource limits: 2 CPU, 1Gi memory (scalable with audit volume)

## Future Enhancements

1. **Horizontal Scaling**
   - Table partitioning by DATE(timestamp)
   - Sharding by aggregate_id for write distribution

2. **Real-time Streaming**
   - WebSocket support for live audit updates
   - Server-Sent Events (SSE) for compliance teams

3. **Advanced Analytics**
   - Elasticsearch integration for full-text search
   - Machine learning anomaly detection

4. **Export Enhancements**
   - PDF export with compliance headers
   - Excel with pivot tables
   - Scheduled report generation

5. **Regulatory Support**
   - Multi-tenancy for regulatory requirements
   - Digital signatures for audit integrity
   - Immutable storage (S3 + WORM)
