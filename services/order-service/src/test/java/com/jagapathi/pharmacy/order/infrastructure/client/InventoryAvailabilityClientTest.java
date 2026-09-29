package com.jagapathi.pharmacy.order.infrastructure.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class InventoryAvailabilityClientTest {

    @Test
    void shouldCallDocumentedAvailabilityEndpointAndParseResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        UUID pharmacyId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();
        BigDecimal quantity = BigDecimal.TEN;

        String expectedUrl = "http://inventory-service:8085/api/v1/inventory/availability"
            + "?pharmacyId=" + pharmacyId + "&medicationId=" + medicationId + "&quantity=" + quantity;

        server.expect(requestTo(expectedUrl))
            .andExpect(method(GET))
            .andRespond(withSuccess("{\"available\":true,\"reason\":\"available\"}", MediaType.APPLICATION_JSON));

        InventoryAvailabilityClient client = new InventoryAvailabilityClient(restTemplate, "http://inventory-service:8085");

        InventoryAvailabilityResponse response = client.checkAvailability(pharmacyId, medicationId, quantity);

        assertThat(response.available()).isTrue();
        assertThat(response.reason()).isEqualTo("available");
        server.verify();
    }

    @Test
    void shouldReturnUnavailableWhenResponseBodyIsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        UUID pharmacyId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();

        server.expect(method(GET))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        InventoryAvailabilityClient client = new InventoryAvailabilityClient(restTemplate, "http://inventory-service:8085");

        InventoryAvailabilityResponse response = client.checkAvailability(pharmacyId, medicationId, null);

        assertThat(response.available()).isFalse();
    }
}
