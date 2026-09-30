# Payment Service Test Status

## Latest verified result

On 2026-09-30, this command completed successfully:

```bash
./mvnw -pl services/payment-service -am test
```

The payment-service module reported 37 tests with 0 failures, 0 errors and
0 skips. Its required `observability` reactor dependency also completed its
own 16-test suite successfully. The payment results cover domain, application,
ownership-client, controller-slice and architecture tests. The old
partial-pass and failing-test counts in this file were stale.

## Integration tests

The `test` lifecycle does not by itself establish the result of the Failsafe
integration-test classes. Run the following to execute the service's complete
verify lifecycle, including configured integration tests:

```bash
./mvnw -pl services/payment-service -am verify
```

Do not infer Testcontainers, database, Kafka or live dependency behavior from
the 37 Surefire tests alone. The controller slice uses
`@AutoConfigureMockMvc(addFilters = false)`, so its HTTP assertions do not
validate the production security filter chain.
