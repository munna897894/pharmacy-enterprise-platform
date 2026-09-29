package com.jagapathi.pharmacy.externalmock.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FailureModeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSwitchFailureMode() throws Exception {
        mockMvc.perform(put("/api/v1/mock/prescription")
                .param("mode", "REJECT"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldServeMockResponse() throws Exception {
        mockMvc.perform(get("/api/v1/mock/payment"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldResetModes() throws Exception {
        mockMvc.perform(post("/api/v1/mock/reset"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldProcessPaymentSuccessfully() throws Exception {
        mockMvc.perform(put("/api/v1/mock/payment").param("mode", "SUCCESS")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/mock/process-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00,\"currency\":\"USD\",\"paymentMethod\":\"CREDIT_CARD\",\"reference\":\"PAY-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionId").value("MOCK-TXN-PAY-1"));

        mockMvc.perform(post("/api/v1/mock/reset")).andExpect(status().isNoContent());
    }

    @Test
    void shouldDeclinePaymentWhenModeIsReject() throws Exception {
        mockMvc.perform(put("/api/v1/mock/payment").param("mode", "REJECT")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/mock/process-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00,\"currency\":\"USD\",\"paymentMethod\":\"CREDIT_CARD\",\"reference\":\"PAY-2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/mock/reset")).andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnServerErrorWhenModeIsError() throws Exception {
        mockMvc.perform(put("/api/v1/mock/payment").param("mode", "ERROR")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/mock/process-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00,\"currency\":\"USD\",\"paymentMethod\":\"CREDIT_CARD\",\"reference\":\"PAY-3\"}"))
            .andExpect(status().isInternalServerError());

        mockMvc.perform(post("/api/v1/mock/reset")).andExpect(status().isNoContent());
    }
}
