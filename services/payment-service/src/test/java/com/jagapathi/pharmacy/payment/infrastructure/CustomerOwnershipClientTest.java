package com.jagapathi.pharmacy.payment.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final CustomerOwnershipClient client =
        new CustomerOwnershipClient(restTemplate, "http://customer-service/");

    @Test
    void forwardsCallerJwtToCustomerProfileEndpoint() {
        UUID customerId = UUID.randomUUID();
        server.expect(requestTo("http://customer-service/api/v1/customers/" + customerId))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer caller-token"))
            .andRespond(withSuccess("{\"id\":\"" + customerId + "\"}", MediaType.APPLICATION_JSON));

        assertThat(client.isOwner(customerId, jwt())).isTrue();
        server.verify();
    }

    @Test
    void treatsForbiddenProfileReadAsNotOwner() {
        UUID customerId = UUID.randomUUID();
        server.expect(requestTo("http://customer-service/api/v1/customers/" + customerId))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(client.isOwner(customerId, jwt())).isFalse();
    }

    @Test
    void surfacesCustomerServiceFailuresInsteadOfGrantingOrDenyingByDefault() {
        UUID customerId = UUID.randomUUID();
        server.expect(requestTo("http://customer-service/api/v1/customers/" + customerId))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.isOwner(customerId, jwt()))
            .isInstanceOf(CustomerOwnershipLookupException.class);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("caller-token")
            .header("alg", "RS256")
            .subject(UUID.randomUUID().toString())
            .build();
    }
}
