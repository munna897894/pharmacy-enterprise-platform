package com.jagapathi.pharmacy.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class CorrelationIdFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = CorrelationIdContext.HEADER_NAME;
    static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    static boolean isValid(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedId = request.getHeader(HEADER_NAME);
        String correlationId = isValid(providedId)
            ? providedId
            : UUID.randomUUID().toString();

        response.setHeader(HEADER_NAME, correlationId);
        HttpServletRequest wrappedRequest = withCorrelationId(request, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            filterChain.doFilter(wrappedRequest, response);
        }
    }

    private HttpServletRequest withCorrelationId(HttpServletRequest request, String correlationId) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return HEADER_NAME.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return HEADER_NAME.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singleton(correlationId))
                    : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                names.add(HEADER_NAME);
                return Collections.enumeration(names);
            }
        };
    }
}
