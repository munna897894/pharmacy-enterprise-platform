package com.jagapathi.pharmacy.product.domain;

import com.jagapathi.pharmacy.product.domain.model.Medication;
import com.jagapathi.pharmacy.product.domain.exception.DuplicateNdcCodeException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MedicationTest {

    @Test
    void should_create_medication_with_valid_data() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma Corp",
                "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");

        assertThat(med.getId()).isNotNull();
        assertThat(med.getNdcCode()).isEqualTo("0001");
        assertThat(med.getName()).isEqualTo("Ibuprofen");
        assertThat(med.getActive()).isTrue();
        assertThat(med.getCreatedAt()).isNotNull();
    }

    @Test
    void should_update_medication() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma Corp",
                "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");

        med.update("Ibuprofen Plus", "Ibuprofen", "New Corp", "Tablet", "250mg", BigDecimal.valueOf(10.99));

        assertThat(med.getName()).isEqualTo("Ibuprofen Plus");
        assertThat(med.getManufacturer()).isEqualTo("New Corp");
        assertThat(med.getUnitPrice()).isEqualTo(BigDecimal.valueOf(10.99));
    }

    @Test
    void should_deactivate_medication() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma Corp",
                "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");

        med.setActive(false);

        assertThat(med.getActive()).isFalse();
    }
}
