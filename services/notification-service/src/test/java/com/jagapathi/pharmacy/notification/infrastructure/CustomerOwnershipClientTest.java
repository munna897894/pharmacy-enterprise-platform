package com.jagapathi.pharmacy.notification.infrastructure;

import com.jagapathi.pharmacy.notification.application.CustomerOwnershipLookupException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomerOwnershipClientTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final String PROFILE_URL = "http://customer-service/api/v1/customers/" + CUSTOMER_ID;

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final CustomerOwnershipClient client = new CustomerOwnershipClient(restTemplate, "http://customer-service/");

    @Test
    void forwardsCallerJwtAndTreatsProfileReadAsOwnership() {
        server.expect(requestTo(PROFILE_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer caller-token"))
            .andRespond(withSuccess());

        assertThat(client.isOwner(CUSTOMER_ID, jwt())).isTrue();
        server.verify();
    }

    @Test
    void forbiddenProfileMeansNotOwner() {
        server.expect(requestTo(PROFILE_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.isOwner(CUSTOMER_ID, jwt())).isFalse();
    }

    @Test
    void missingProfileMeansNotOwner() {
        server.expect(requestTo(PROFILE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.isOwner(CUSTOMER_ID, jwt())).isFalse();
    }

    @Test
    void customerServiceFailureIsSurfacedNotGuessed() {
        server.expect(requestTo(PROFILE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.isOwner(CUSTOMER_ID, jwt()))
            .isInstanceOf(CustomerOwnershipLookupException.class);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("caller-token")
            .header("alg", "RS256")
            .subject("550e8400-e29b-41d4-a716-446655440000")
            .build();
    }
}
