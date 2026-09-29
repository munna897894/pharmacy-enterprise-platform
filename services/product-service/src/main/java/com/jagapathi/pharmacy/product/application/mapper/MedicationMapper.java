package com.jagapathi.pharmacy.product.application.mapper;

import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.domain.model.Medication;
import org.springframework.stereotype.Component;

@Component
public class MedicationMapper {

    public MedicationResponse toResponse(Medication medication) {
        return new MedicationResponse(
                medication.getId(),
                medication.getNdcCode(),
                medication.getName(),
                medication.getGenericName(),
                medication.getManufacturer(),
                medication.getDosageForm(),
                medication.getStrength(),
                medication.getUnitPrice(),
                medication.getCurrency(),
                medication.getActive(),
                medication.getVersion(),
                medication.getCreatedAt(),
                medication.getUpdatedAt()
        );
    }
}
