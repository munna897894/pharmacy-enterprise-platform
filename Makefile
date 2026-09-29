MVNW := ./mvnw

.PHONY: verify
verify:
	$(MVNW) clean verify
