package com.jagapathi.pharmacy.product.application.service;

import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.api.response.PageResponse;
import com.jagapathi.pharmacy.product.domain.model.Medication;
import com.jagapathi.pharmacy.product.domain.exception.DuplicateNdcCodeException;
import com.jagapathi.pharmacy.product.domain.exception.MedicationNotFoundException;
import com.jagapathi.pharmacy.product.domain.repository.MedicationRepository;
import com.jagapathi.pharmacy.product.application.mapper.MedicationMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@Service
@Transactional
public class MedicationService {

    private static final Logger log = LoggerFactory.getLogger(MedicationService.class);

    private final MedicationRepository medicationRepository;
    private final MedicationMapper medicationMapper;

    public MedicationService(MedicationRepository medicationRepository, MedicationMapper medicationMapper) {
        this.medicationRepository = medicationRepository;
        this.medicationMapper = medicationMapper;
    }

    @CacheEvict(value = "medicationSearch", allEntries = true)
    @RecordBusinessMetric(BusinessMetric.MEDICATION_CATALOG)
    public MedicationResponse createMedication(String ndcCode, String name, String genericName,
                                               String manufacturer, String dosageForm, String strength,
                                               BigDecimal unitPrice, String currency) {
        if (medicationRepository.findByNdcCode(ndcCode).isPresent()) {
            throw new DuplicateNdcCodeException(ndcCode);
        }

        Medication medication = new Medication(ndcCode, name, genericName, manufacturer,
                                                dosageForm, strength, unitPrice, currency);
        Medication saved = medicationRepository.save(medication);
        log.info("Created medication: {} with NDC: {}", saved.getId(), ndcCode);
        return medicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "medications", key = "#id", unless = "#result == null")
    public MedicationResponse getMedicationById(String id) {
        Medication medication = medicationRepository.findById(id)
                .orElseThrow(() -> new MedicationNotFoundException(id));
        return medicationMapper.toResponse(medication);
    }

    @Caching(evict = {
            @CacheEvict(value = "medications", key = "#id"),
            @CacheEvict(value = "medicationSearch", allEntries = true)
    })
    @RecordBusinessMetric(BusinessMetric.MEDICATION_CATALOG)
    public MedicationResponse updateMedication(String id, String name, String genericName,
                                               String manufacturer, String dosageForm, String strength,
                                               BigDecimal unitPrice) {
        Medication medication = medicationRepository.findById(id)
                .orElseThrow(() -> new MedicationNotFoundException(id));

        medication.update(name, genericName, manufacturer, dosageForm, strength, unitPrice);
        Medication saved = medicationRepository.save(medication);
        log.info("Updated medication: {} ", id);
        return medicationMapper.toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "medications", key = "#id"),
            @CacheEvict(value = "medicationSearch", allEntries = true)
    })
    public MedicationResponse updateMedicationStatus(String id, Boolean active) {
        Medication medication = medicationRepository.findById(id)
                .orElseThrow(() -> new MedicationNotFoundException(id));

        medication.setActive(active);
        Medication saved = medicationRepository.save(medication);
        log.info("Updated medication status: {} to active={}", id, active);
        return medicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "medicationSearch", key = "#query + ':' + #manufacturer + ':' + #dosageForm + ':' + #active + ':' + #pageable.pageNumber + ':' + #pageable.pageSize", unless = "#result == null")
    public PageResponse<MedicationResponse> searchMedications(String query, String manufacturer,
                                                              String dosageForm, Boolean active,
                                                              Pageable pageable) {
        Page<Medication> page = medicationRepository.searchMedications(query, manufacturer, dosageForm, active, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(medicationMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
