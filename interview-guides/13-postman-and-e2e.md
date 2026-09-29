# 13 — Postman and End-to-End Testing (Prompt 13)

## Summary

This area is **PARTIALLY IMPLEMENTED, but critically incomplete** in the real repo. There are real shell scripts (`scripts/newman.sh`, `scripts/smoke-test.sh`, `scripts/e2e-test.sh`), a tiny `docs/e2e-runbook.md`, and the testing strategy explicitly calls for Postman/Newman-style E2E coverage — but the actual **`postman/` directory and collection/environment JSON files are missing**.

So the honest interview answer is: the repo has **E2E intent plus placeholders**, not a finished Postman/Newman automation suite. That makes this guide a mix of real findings and theory about how a proper E2E setup should work.

## Diagram — current vs intended E2E flow

```text
CURRENT REPO STATE (real)

Developer
  │
  ├──► ./scripts/smoke-test.sh   -> checks JWKS, optional health
  ├──► ./scripts/e2e-test.sh     -> currently only checks JWKS
  └──► ./scripts/newman.sh       -> references missing Postman files

INTENDED PROMPT-13 STATE (not fully implemented)

Postman collection / Newman CI run
  │
  ├── login -> store access token / refresh token in environment vars
  ├── create/read product/customer/pharmacy data
  ├── create order -> poll async status
  ├── validate payment/inventory/notification/audit outcomes
  ├── run negative cases (400/401/403/404/409/429)
  ├── collect X-Correlation-ID values for debugging
  └── exit non-zero on failures with bounded polling and cleanup
```

## Key files

| File | What it shows |
|---|---|
| `scripts/newman.sh` | Real Newman wrapper, but it points to `postman/pharmacy.postman_collection.json` and `postman/local.postman_environment.json`, which do not exist. Great evidence of partial implementation + broken wiring. |
| `scripts/smoke-test.sh` | Very thin curl smoke script: checks JWKS and attempts `/actuator/health`. Good for explaining a smoke test, but far short of a multi-service E2E workflow. |
| `scripts/e2e-test.sh` | Also extremely thin: it only checks the JWKS endpoint. Despite its name, it is not a real end-to-end scenario runner yet. |
| `docs/e2e-runbook.md` | Real but skeletal documentation: says to use seeded local environment and run smoke/Newman flows, with cleanup via `reset-demo-data.sh`. |
| `docs/09-testing-strategy.md` | States that end-to-end testing should exist and explicitly names Postman/Newman or REST-assured as the tool choice. Helpful for understanding intended quality level. |
| `scripts/reset-demo-data.sh` | Real cleanup hook referenced by the runbook; useful for discussing deterministic E2E data and reset-to-known-state practices. |
| `docs/03-repository-structure.md` | Important discrepancy file: it documents a `postman/` directory that is not actually present in the repo. |
| `(missing) postman/` | The biggest finding: there is no checked-in collection, environment, pre-request scripts, or request assertions to study. |

## Core concepts

| Concept | Explanation | Why it matters |
|---|---|---|
| Contract testing vs end-to-end testing | Contract tests verify API/event compatibility in isolation; E2E tests validate a full user/business flow across real running components. | Interviewers want candidates to know E2E is not a substitute for contract or integration tests. |
| Test pyramid | Most tests should be unit/integration, with a relatively thin E2E layer at the top. | Thick E2E suites are slow, flaky, and expensive to debug. |
| Postman collection structure | Collections are usually organized into folders by domain/service or scenario, with reusable auth steps and request-level assertions. | Good organization keeps collections maintainable as systems grow. |
| Environment variables | Base URLs, tokens, correlation IDs, resource IDs, and test toggles can be stored in Postman environments. | This lets one collection run in local, test, or ephemeral environments without hard-coded values. |
| Variable chaining | One request can capture a token or UUID and feed it into later requests. | E2E workflows depend on this heavily because later calls need outputs from earlier ones. |
| Pre-request scripts and tests | Pre-request scripts prepare auth headers or data; test scripts assert status codes, headers, response fields, and save variables. | This is what turns Postman from a manual client into a repeatable automation tool. |
| Newman | Newman is the CLI runner for Postman collections, suitable for CI. | It makes collections executable headlessly so failures produce non-zero exit codes in pipelines. |
| Bounded polling | Async workflows like order sagas should poll with a timeout and a max attempt count. | Unbounded polling can hang CI indefinitely and hide stuck states. |
| Negative-path coverage | Good E2E suites verify unauthorized, forbidden, validation, conflict, and rate-limit behavior — not just happy path. | Production outages often hide in error handling and auth edges, not the happy path. |
| Deterministic seed/reset strategy | E2E tests should create isolated data or reset to a known baseline before and after execution. | This keeps suites repeatable and reduces flaky test failures from dirty shared state. |
| Thin E2E, rich lower layers | E2E should validate the critical workflows, while unit/integration tests handle most branch-level logic. | This keeps pipeline time manageable and failure triage faster. |
| Sanitized diagnostics | Good E2E runners print enough failure detail to debug but avoid echoing secrets or tokens. | Prompt 13 explicitly asked for useful sanitized diagnostics. |

## Common interview Q&A

**Q: Why shouldn't E2E tests be the majority of a test suite?**  
A: They are the slowest, most brittle, and most expensive to diagnose because many components can fail for unrelated reasons. A healthy test pyramid catches most bugs at the unit, slice, and integration layers before E2E even runs.

**Q: What should a good Postman collection capture between requests?**  
A: At minimum: access token, refresh token if relevant, resource IDs created during the flow, and correlation IDs from response headers. Those variables let later requests stay realistic and debuggable.

**Q: What makes Newman useful in CI?**  
A: It runs collections non-interactively, returns a non-zero exit code on failure, and can emit machine-readable reports or logs. That turns a manual Postman workflow into a real pipeline gate.

**Q: How would you test an asynchronous order saga in Postman/Newman?**  
A: Create the order, then poll the order status endpoint with a bounded timeout or max attempt count until it reaches a terminal state like `CONFIRMED` or `CANCELLED_*`. Record correlation IDs so failures can be traced through logs and metrics.

**Q: What's the difference between a smoke test and a true E2E test?**  
A: A smoke test verifies a tiny “is the platform basically up?” slice, often health or auth essentials. A true E2E test validates a business workflow across multiple services and state transitions.

**Q: Why is deterministic test data important for E2E?**  
A: Because hidden dependencies on leftover data make failures flaky and irreproducible. Stable fictional seed data and explicit cleanup make troubleshooting much faster.

**Q: What kinds of assertions matter most in API E2E tests?**  
A: Status code, schema-critical fields, headers like `X-Correlation-ID`, final workflow state, and absence/presence of side effects such as notifications or audit records.

**Q: If Postman files are missing, what does the repo still teach you?**  
A: It teaches the architecture's desired testing shape: smoke scripts, reset hooks, operational runbook, and explicit expectation that E2E belongs above integration tests — just not yet fully implemented.

**Q: Why should async polling be bounded?**  
A: Because a stuck saga should fail fast with a useful error rather than hang forever. In CI, bounded retries and timeout ceilings protect pipeline reliability.

**Q: What is the difference between contract testing and E2E when debugging a failure?**  
A: Contract failures usually point to interface mismatch between two components; E2E failures can come from any layer — auth, routing, data setup, async timing, or side-effect validation. That is why E2E should stay focused on a few critical flows.

## Gotchas / real findings

- **The `postman/` directory is missing.** This is the single biggest repo truth for Prompt 13.
- **`scripts/newman.sh` is currently broken by construction** because it references missing files: `postman/pharmacy.postman_collection.json` and `postman/local.postman_environment.json`.
- **`scripts/e2e-test.sh` is effectively a placeholder.** It only checks the JWKS endpoint, so it does not exercise product/order/payment/notification/audit workflows.
- **`scripts/smoke-test.sh` is also minimal.** It checks JWKS and tries health, but does not authenticate, create data, or validate a saga.
- **`docs/e2e-runbook.md` exists but is skeletal.** It mentions seeded environment and cleanup, yet does not document the scenarios, polling logic, expected states, or troubleshooting depth requested in the prompt.
- **The testing strategy expects much more than exists.** `docs/09-testing-strategy.md` calls for Postman/Newman E2E and lists critical workflow tests, but the checked-in assets do not yet implement those flows.
- **The repo does have useful supporting pieces** like `reset-demo-data.sh`; so this is not “zero work done,” but it is far from a production-quality E2E harness.
- **Prompt 13 wanted negative folders, async polling, idempotency checks, and failure-path scenarios** such as inventory rejection, payment failure, duplicate requests, and poison notification — none of those are present as runnable Postman assets.
- **No collection-level secret handling was found** because no collection/environment JSON exists. That means there is nothing yet proving tokens are stored safely and not printed.
- **No CI integration around Newman exists** beyond the placeholder shell wrapper. There is no workflow job uploading Newman reports or failing PRs based on E2E outcomes.
- **Interview nuance:** this is a great example of “documentation and wrapper scripts can create the illusion of completion.” Always verify the actual collection/environment files exist.

## Trace-through

**Real current-state example: what the repo can actually do today**

1. A developer runs `./scripts/smoke-test.sh`.
2. The script defaults `base_url` to `http://localhost:8080`.
3. It verifies the gateway can serve the auth JWKS endpoint.
4. It also tries `/actuator/health`, but the command is tolerant of failure (`|| true`), so even that is not a strict gate.
5. A developer might then run `./scripts/newman.sh` expecting a collection-driven E2E flow.
6. Newman immediately depends on `postman/pharmacy.postman_collection.json` and `postman/local.postman_environment.json`.
7. Because those files are absent, the intended collection-based E2E workflow cannot actually run from the checked-in repo state.
8. So the **real** end-to-end capability today is closer to a smoke/proof-of-life check than a full Prompt 13 automation suite.

**If Prompt 13 were completed properly, the ideal flow would be:**

9. Login at the gateway and store the token in an environment variable.
10. Create or read deterministic product/customer/pharmacy data.
11. Submit an order, capture `X-Correlation-ID`, and poll status to a terminal state.
12. Assert payment/inventory/notification/audit outcomes, run negative and duplicate-request scenarios, then clean up/reset.
