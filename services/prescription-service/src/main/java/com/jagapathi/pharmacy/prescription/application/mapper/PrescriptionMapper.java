package com.jagapathi.pharmacy.prescription.application.mapper;

import com.jagapathi.pharmacy.prescription.api.response.PrescriptionLineResponse;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionResponse;
import com.jagapathi.pharmacy.prescription.domain.model.Prescription;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionLine;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PrescriptionMapper {
    
    public PrescriptionResponse toResponse(Prescription prescription) {
        return new PrescriptionResponse(
            prescription.getId(),
            prescription.getCustomerId(),
            prescription.getPrescriberId(),
            prescription.getPrescribedAt(),
            prescription.getExpiresAt(),
            prescription.getStatus().name(),
            prescription.getVersion(),
            prescription.getCreatedAt(),
            prescription.getUpdatedAt(),
            prescription.getLines().stream()
                .map(this::lineToResponse)
                .collect(Collectors.toList())
        );
    }
    
    private PrescriptionLineResponse lineToResponse(PrescriptionLine line) {
        return new PrescriptionLineResponse(
            line.getId(),
            line.getPrescriptionId(),
            line.getProductId(),
            line.getQuantity(),
            line.getInstructions(),
            line.getDispensedQuantity(),
            line.getFilledAt(),
            line.getCreatedAt()
        );
    }
}
