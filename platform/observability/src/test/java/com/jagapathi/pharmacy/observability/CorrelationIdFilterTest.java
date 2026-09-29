package com.jagapathi.pharmacy.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeCorrelationIdAndSetsResponseAndMdc() throws Exception {
        String expectedId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, expectedId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            assertThat(((jakarta.servlet.http.HttpServletRequest) wrappedRequest)
                .getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(expectedId);
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(expectedId);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(expectedId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidCorrelationIdWithUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> { });

        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER_NAME))).isNotNull();
    }
}
