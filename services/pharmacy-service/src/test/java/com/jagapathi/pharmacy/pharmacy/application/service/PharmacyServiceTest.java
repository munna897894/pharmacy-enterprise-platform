package com.jagapathi.pharmacy.pharmacy.application.service;

import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyAddressRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.UpdatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.response.PharmacyResponse;
import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyAuthorizationException;
import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyNotFoundException;
import com.jagapathi.pharmacy.pharmacy.domain.model.Pharmacy;
import com.jagapathi.pharmacy.pharmacy.domain.model.PharmacyAddress;
import com.jagapathi.pharmacy.pharmacy.domain.repository.BusinessHourRepository;
import com.jagapathi.pharmacy.pharmacy.domain.repository.PharmacyAddressRepository;
import com.jagapathi.pharmacy.pharmacy.domain.repository.PharmacyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceTest {

    @Mock
    private PharmacyRepository pharmacyRepository;

    @Mock
    private PharmacyAddressRepository addressRepository;

    @Mock
    private BusinessHourRepository businessHourRepository;

    private PharmacyService pharmacyService;

    @BeforeEach
    void setUp() {
        pharmacyService = new PharmacyService(pharmacyRepository, addressRepository, businessHourRepository);
    }

    @Test
    void testGetPharmacy_Success() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        when(pharmacyRepository.findById("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(pharmacy));
        when(addressRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.empty());
        when(businessHourRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(List.of());

        PharmacyResponse response = pharmacyService.getPharmacy("p1000000-0000-0000-0000-000000000001");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Downtown Pharmacy");
    }

    @Test
    void testGetPharmacy_NotFound() {
        when(pharmacyRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pharmacyService.getPharmacy("nonexistent"))
                .isInstanceOf(PharmacyNotFoundException.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testCreatePharmacy_Success(CapturedOutput output) {
        CreatePharmacyRequest request = new CreatePharmacyRequest(
                "Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York"
        );
        Pharmacy pharmacy = new Pharmacy(request.name(), request.licenseNumber(), request.phone(), request.timezone());
        when(pharmacyRepository.save(any(Pharmacy.class))).thenReturn(pharmacy);

        PharmacyResponse response = pharmacyService.createPharmacy(request, List.of("ROLE_ADMIN"));

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Downtown Pharmacy");
        assertThat(output).contains("pharmacy.created pharmacyId=" + pharmacy.getId() + " outcome=created")
                .doesNotContain("LIC-001-2024", "(212) 555-0100");
    }

    @Test
    void testCreatePharmacy_UnauthorizedNonAdmin() {
        CreatePharmacyRequest request = new CreatePharmacyRequest(
                "Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York"
        );

        assertThatThrownBy(() -> pharmacyService.createPharmacy(request, List.of("ROLE_STORE_MANAGER")))
                .isInstanceOf(PharmacyAuthorizationException.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testUpdatePharmacy_Success(CapturedOutput output) {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        UpdatePharmacyRequest request = new UpdatePharmacyRequest("Updated Downtown Pharmacy", "(212) 555-0200");
        when(pharmacyRepository.findById("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(pharmacy));
        when(pharmacyRepository.save(any(Pharmacy.class))).thenReturn(pharmacy);
        when(addressRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.empty());
        when(businessHourRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(List.of());

        PharmacyResponse response = pharmacyService.updatePharmacy("p1000000-0000-0000-0000-000000000001", request, List.of("ROLE_STORE_MANAGER"));

        assertThat(response).isNotNull();
        assertThat(output).contains("pharmacy.updated pharmacyId=" + pharmacy.getId() + " outcome=updated")
                .doesNotContain("(212) 555-0200");
    }

    @Test
    void testUpdatePharmacy_UnauthorizedNonStaffRole() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        UpdatePharmacyRequest request = new UpdatePharmacyRequest("Updated Downtown Pharmacy", "(212) 555-0200");

        assertThatThrownBy(() -> pharmacyService.updatePharmacy("p1000000-0000-0000-0000-000000000001", request, List.of("ROLE_CUSTOMER")))
                .isInstanceOf(PharmacyAuthorizationException.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testUpdatePharmacyStatus_Success(CapturedOutput output) {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        when(pharmacyRepository.findById("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(pharmacy));
        when(pharmacyRepository.save(any(Pharmacy.class))).thenReturn(pharmacy);
        when(addressRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.empty());
        when(businessHourRepository.findByPharmacyId("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(List.of());

        PharmacyResponse response = pharmacyService.updatePharmacyStatus("p1000000-0000-0000-0000-000000000001", "CLOSED", List.of("ROLE_ADMIN"));

        assertThat(response).isNotNull();
        assertThat(output).contains("pharmacy.status.updated pharmacyId=" + pharmacy.getId() + " status=CLOSED");
    }

    @Test
    void testAddPharmacyAddress_Success() {
        Pharmacy pharmacy = new Pharmacy("Downtown Pharmacy", "LIC-001-2024", "(212) 555-0100", "America/New_York");
        CreatePharmacyAddressRequest request = new CreatePharmacyAddressRequest(
                "123 Main Street", null, "New York", "NY", "10001", new BigDecimal("40.71280"), new BigDecimal("-74.00602")
        );
        PharmacyAddress address = new PharmacyAddress(
                "p1000000-0000-0000-0000-000000000001", request.line1(), request.line2(), 
                request.city(), request.state(), request.postalCode(), request.latitude(), request.longitude()
        );

        when(pharmacyRepository.findById("p1000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(pharmacy));
        when(addressRepository.save(any(PharmacyAddress.class))).thenReturn(address);

        var response = pharmacyService.addPharmacyAddress("p1000000-0000-0000-0000-000000000001", request);

        assertThat(response).isNotNull();
        assertThat(response.line1()).isEqualTo("123 Main Street");
    }

    @Test
    void testAddPharmacyAddress_PharmacyNotFound() {
        CreatePharmacyAddressRequest request = new CreatePharmacyAddressRequest(
                "123 Main Street", null, "New York", "NY", "10001", new BigDecimal("40.71280"), new BigDecimal("-74.00602")
        );
        when(pharmacyRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pharmacyService.addPharmacyAddress("nonexistent", request))
                .isInstanceOf(PharmacyNotFoundException.class);
    }
}
