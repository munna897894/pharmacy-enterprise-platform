package com.jagapathi.pharmacy.pharmacy.application.service;

import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyAddressRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.UpdatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.response.BusinessHourResponse;
import com.jagapathi.pharmacy.pharmacy.api.response.PharmacyAddressResponse;
import com.jagapathi.pharmacy.pharmacy.api.response.PharmacyResponse;
import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyAuthorizationException;
import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyNotFoundException;
import com.jagapathi.pharmacy.pharmacy.domain.model.BusinessHour;
import com.jagapathi.pharmacy.pharmacy.domain.model.Pharmacy;
import com.jagapathi.pharmacy.pharmacy.domain.model.PharmacyAddress;
import com.jagapathi.pharmacy.pharmacy.domain.repository.BusinessHourRepository;
import com.jagapathi.pharmacy.pharmacy.domain.repository.PharmacyAddressRepository;
import com.jagapathi.pharmacy.pharmacy.domain.repository.PharmacyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyAddressRepository addressRepository;
    private final BusinessHourRepository businessHourRepository;

    public PharmacyService(PharmacyRepository pharmacyRepository,
                         PharmacyAddressRepository addressRepository,
                         BusinessHourRepository businessHourRepository) {
        this.pharmacyRepository = pharmacyRepository;
        this.addressRepository = addressRepository;
        this.businessHourRepository = businessHourRepository;
    }

    @Transactional(readOnly = true)
    public PharmacyResponse getPharmacy(String pharmacyId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));
        
        PharmacyAddressResponse address = addressRepository.findByPharmacyId(pharmacyId)
                .map(PharmacyAddressResponse::from)
                .orElse(null);
        
        List<BusinessHourResponse> hours = businessHourRepository.findByPharmacyId(pharmacyId)
                .stream()
                .map(BusinessHourResponse::from)
                .toList();
        
        return PharmacyResponse.from(pharmacy, address, hours);
    }

    @Transactional(readOnly = true)
    public List<PharmacyResponse> searchPharmacies(String status, String postalCode) {
        return pharmacyRepository.searchPharmacies(status, postalCode).stream()
                .map(pharmacy -> {
                    PharmacyAddressResponse address = addressRepository.findByPharmacyId(pharmacy.getId())
                            .map(PharmacyAddressResponse::from)
                            .orElse(null);
                    List<BusinessHourResponse> hours = businessHourRepository.findByPharmacyId(pharmacy.getId())
                            .stream()
                            .map(BusinessHourResponse::from)
                            .toList();
                    return PharmacyResponse.from(pharmacy, address, hours);
                })
                .toList();
    }

    public PharmacyResponse createPharmacy(CreatePharmacyRequest request, List<String> roles) {
        if (!hasRole(roles, "ADMIN")) {
            throw new PharmacyAuthorizationException("User");
        }

        Pharmacy pharmacy = new Pharmacy(
                request.name(),
                request.licenseNumber(),
                request.phone(),
                request.timezone()
        );
        
        Pharmacy saved = pharmacyRepository.save(pharmacy);
        return PharmacyResponse.from(saved, null, List.of());
    }

    public PharmacyResponse updatePharmacy(String pharmacyId, UpdatePharmacyRequest request, List<String> roles) {
        if (!hasRole(roles, "STORE_MANAGER", "ADMIN")) {
            throw new PharmacyAuthorizationException("User");
        }

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));
        
        pharmacy.update(request.name(), request.phone());
        Pharmacy updated = pharmacyRepository.save(pharmacy);
        
        PharmacyAddressResponse address = addressRepository.findByPharmacyId(pharmacyId)
                .map(PharmacyAddressResponse::from)
                .orElse(null);
        
        List<BusinessHourResponse> hours = businessHourRepository.findByPharmacyId(pharmacyId)
                .stream()
                .map(BusinessHourResponse::from)
                .toList();
        
        return PharmacyResponse.from(updated, address, hours);
    }

    public PharmacyResponse updatePharmacyStatus(String pharmacyId, String newStatus, List<String> roles) {
        if (!hasRole(roles, "STORE_MANAGER", "ADMIN")) {
            throw new PharmacyAuthorizationException("User");
        }

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));
        
        pharmacy.setStatus(newStatus);
        Pharmacy updated = pharmacyRepository.save(pharmacy);
        
        PharmacyAddressResponse address = addressRepository.findByPharmacyId(pharmacyId)
                .map(PharmacyAddressResponse::from)
                .orElse(null);
        
        List<BusinessHourResponse> hours = businessHourRepository.findByPharmacyId(pharmacyId)
                .stream()
                .map(BusinessHourResponse::from)
                .toList();
        
        return PharmacyResponse.from(updated, address, hours);
    }

    public PharmacyAddressResponse addPharmacyAddress(String pharmacyId, CreatePharmacyAddressRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new PharmacyNotFoundException(pharmacyId));

        PharmacyAddress address = new PharmacyAddress(
                pharmacyId,
                request.line1(),
                request.line2(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.latitude(),
                request.longitude()
        );
        
        PharmacyAddress saved = addressRepository.save(address);
        return PharmacyAddressResponse.from(saved);
    }

    private boolean hasRole(List<String> roles, String... requiredRoles) {
        if (roles == null) return false;
        for (String required : requiredRoles) {
            if (roles.contains("ROLE_" + required)) {
                return true;
            }
        }
        return false;
    }
}
