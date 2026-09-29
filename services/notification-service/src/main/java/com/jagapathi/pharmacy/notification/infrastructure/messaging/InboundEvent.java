package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Accepts both the documented common envelope ({@code payload} object) and the flat payloads
 * some producers still emit. Flat OrderCreated carries no eventType, so it is inferred.
 */
record InboundEvent(String eventId, String eventType, JsonNode body) {

    static final String ORDER_CREATED = "OrderCreated";

    static InboundEvent parse(ObjectMapper objectMapper, String json) {
        JsonNode root;
        try {
            root = objectMapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .readTree(json);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Event is not valid JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new MalformedEventException("Event must be a JSON object");
        }
        JsonNode body = root.path("payload").isObject() ? root.get("payload") : root;
        String eventId = text(root, "eventId");
        if (eventId == null) {
            throw new MalformedEventException("Event is missing eventId");
        }
        return new InboundEvent(eventId, resolveType(root, body), body);
    }

    private static String resolveType(JsonNode root, JsonNode body) {
        String declared = text(root, "eventType");
        if (declared != null) {
            return declared;
        }
        if (body.hasNonNull("orderId") && body.hasNonNull("customerId") && body.has("items")) {
            return ORDER_CREATED;
        }
        return null;
    }

    UUID uuid(String field) {
        String value = text(body, field);
        if (value == null) {
            throw new MalformedEventException(eventType + " is missing " + field);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException(eventType + " has invalid " + field, e);
        }
    }

    String optionalText(String field) {
        return text(body, field);
    }

    BigDecimal optionalDecimal(String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
