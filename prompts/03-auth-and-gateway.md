# Prompt 03 — Authentication and API Gateway (current state)

This prompt is aligned to the actual implementation that exists in the repo today.

## Status

Complete for the gateway and auth service scope.

The repo contains:

- `services/auth-service`
- `services/api-gateway`

These are wired together using JWT/OAuth2 resource-server validation, service routing, and correlation ID propagation.

## What is implemented

### Auth service

Includes:

- user and role model with Flyway-managed persistence
- login/register/refresh/logout flows
- BCrypt-based password hashing
- RS256 JWT issuance with standard claims
- JWKS/public-key exposure for gateway validation
- refresh-token rotation and invalidation logic
- local seed users for the learning environment
- logging that omits credentials and tokens
- MySQL-backed persistence and security-focused tests

### API gateway

Includes:

- Spring Cloud Gateway WebFlux routing for auth and downstream services
- JWT validation using the auth service public key/JWKS
- allowlisted routes for public auth and health endpoints
- default authenticated policy with downstream validation still expected
- correlation ID creation and propagation
- Redis-backed rate limiting
- configurable CORS
- sanitized global error handling
- actuator and metrics exposure
- timeout handling and route-based security

## Current runtime assumptions

The local platform deliberately uses host-based service discovery for MySQL and Kafka during local execution, with Redis retained as a Kubernetes-local dependency in the local cluster setup.

The gateway and service configs are environment-driven rather than hard-coded to a single bootstrap environment. This was a critical fix during the local runtime stabilization pass.

## Operational notes

The real project drift was not in the auth or gateway design itself; it was in the runtime values and deployment configuration. These were corrected across the platform so the auth service, gateway, and downstream services could work together reliably in Docker/Kubernetes.

## Current validation checks

The auth+gateway stack is expected to satisfy:

- protected routes reject missing/invalid tokens
- role-based restrictions are enforced by gateway and service rules
- correlation IDs stay consistent across request and response
- login bursts are rate-limited
- health and auth endpoints remain accessible without unnecessary authentication

