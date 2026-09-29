package com.jagapathi.pharmacy.pharmacy.domain;

import com.jagapathi.pharmacy.pharmacy.domain.model.Pharmacy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class PharmacyTest {

    @Test
    void testCreatePharmacy() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");

        assertThat(pharmacy.getId()).isNotNull();
        assertThat(pharmacy.getName()).isEqualTo("Downtown Pharmacy");
        assertThat(pharmacy.getLicenseNumber()).isEqualTo("LIC-001-2024");
        assertThat(pharmacy.getPhone()).isEqualTo("(212) 555-0100");
        assertThat(pharmacy.getStatus()).isEqualTo("OPEN");
        assertThat(pharmacy.getTimezone()).isEqualTo("America/New_York");
        // New entities keep version == null until first persist; Hibernate then
        // initializes @Version to 0 on insert (see optimistic-locking fix).
        assertThat(pharmacy.getVersion()).isNull();
    }

    @Test
    void testUpdatePharmacy() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        Instant originalUpdatedAt = pharmacy.getUpdatedAt();

        pharmacy.update("Updated Downtown Pharmacy", "(212) 555-0200");

        assertThat(pharmacy.getName()).isEqualTo("Updated Downtown Pharmacy");
        assertThat(pharmacy.getPhone()).isEqualTo("(212) 555-0200");
        assertThat(pharmacy.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void testSetStatusOpenToClosed() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");

        pharmacy.setStatus("CLOSED");

        assertThat(pharmacy.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void testSetStatusOpenToDisabled() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");

        pharmacy.setStatus("DISABLED");

        assertThat(pharmacy.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void testSetStatusInvalidTransition() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        pharmacy.setStatus("DISABLED");

        assertThatThrownBy(() -> pharmacy.setStatus("OPEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void testSetStatusSameStatus() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");

        pharmacy.setStatus("OPEN");

        assertThat(pharmacy.getStatus()).isEqualTo("OPEN");
    }
}
