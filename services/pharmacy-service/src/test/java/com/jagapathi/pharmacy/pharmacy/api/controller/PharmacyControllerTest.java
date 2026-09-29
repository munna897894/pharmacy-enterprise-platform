package com.jagapathi.pharmacy.pharmacy.api.controller;

import com.jagapathi.pharmacy.pharmacy.application.service.PharmacyService;
import com.jagapathi.pharmacy.pharmacy.infrastructure.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PharmacyController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class PharmacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PharmacyService pharmacyService;

    @Test
    void testCreatePharmacy_MissingName() throws Exception {
        String request = """
                {
                    "licenseNumber": "LIC-001-2024",
                    "phone": "(212) 555-0100",
                    "timezone": "America/New_York"
                }
                """;

        mockMvc.perform(post("/api/v1/pharmacies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testCreatePharmacy_InvalidPhoneLengthTooShort() throws Exception {
        String request = """
                {
                    "name": "Downtown Pharmacy",
                    "licenseNumber": "LIC-001-2024",
                    "phone": "555",
                    "timezone": "America/New_York"
                }
                """;

        mockMvc.perform(post("/api/v1/pharmacies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testCreatePharmacy_MissingLicenseNumber() throws Exception {
        String request = """
                {
                    "name": "Downtown Pharmacy",
                    "phone": "(212) 555-0100",
                    "timezone": "America/New_York"
                }
                """;

        mockMvc.perform(post("/api/v1/pharmacies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testUpdatePharmacy_InvalidName() throws Exception {
        String request = """
                {
                    "name": "X",
                    "phone": "(212) 555-0100"
                }
                """;

        mockMvc.perform(put("/api/v1/pharmacies/p1000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testUpdatePharmacyStatus_InvalidStatus() throws Exception {
        String request = """
                {
                    "status": "INVALID_STATUS"
                }
                """;

        mockMvc.perform(patch("/api/v1/pharmacies/p1000000-0000-0000-0000-000000000001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSearchPharmacies_NoFilters() throws Exception {
        mockMvc.perform(get("/api/v1/pharmacies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetPharmacy() throws Exception {
        mockMvc.perform(get("/api/v1/pharmacies/p1000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void testAddPharmacyAddress_InvalidLatitude() throws Exception {
        String request = """
                {
                    "line1": "123 Main Street",
                    "city": "New York",
                    "state": "NY",
                    "postalCode": "10001",
                    "latitude": 91.0,
                    "longitude": -74.00602
                }
                """;

        mockMvc.perform(post("/api/v1/pharmacies/p1000000-0000-0000-0000-000000000001/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testAddPharmacyAddress_InvalidLongitude() throws Exception {
        String request = """
                {
                    "line1": "123 Main Street",
                    "city": "New York",
                    "state": "NY",
                    "postalCode": "10001",
                    "latitude": 40.71280,
                    "longitude": 181.0
                }
                """;

        mockMvc.perform(post("/api/v1/pharmacies/p1000000-0000-0000-0000-000000000001/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
