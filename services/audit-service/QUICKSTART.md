# Audit Service Quick Start Guide

## Build & Test

```bash
# Compile the service
mvn -pl services/audit-service clean compile

# Run all tests
mvn -pl services/audit-service test

# Run specific test class
mvn -pl services/audit-service -Dtest=AuditLogServiceTest test

# Build package (without tests)
mvn -pl services/audit-service clean package -DskipTests

# Full build with tests
mvn -pl services/audit-service clean package
```

## Running Locally

```bash
# Start the service (requires Kafka, MySQL running)
mvn -pl services/audit-service spring-boot:run

# Or run the JAR directly
java -jar services/audit-service/target/audit-service-0.0.1-SNAPSHOT.jar
```

## Database Setup

```bash
# Create MySQL schema and user
mysql -u root -p << EOF
CREATE SCHEMA audit_service;
CREATE USER 'audit_user'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON audit_service.* TO 'audit_user'@'%';
FLUSH PRIVILEGES;
