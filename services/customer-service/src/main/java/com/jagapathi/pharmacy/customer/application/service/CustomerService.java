package com.jagapathi.pharmacy.customer.application.service;

import com.jagapathi.pharmacy.customer.api.response.CustomerAddressResponse;
import com.jagapathi.pharmacy.customer.api.response.CustomerResponse;
import com.jagapathi.pharmacy.customer.domain.exception.CustomerAccessDeniedException;
import com.jagapathi.pharmacy.customer.domain.exception.CustomerNotFoundException;
import com.jagapathi.pharmacy.customer.domain.model.Customer;
import com.jagapathi.pharmacy.customer.domain.model.CustomerAddress;
import com.jagapathi.pharmacy.customer.domain.repository.CustomerAddressRepository;
import com.jagapathi.pharmacy.customer.domain.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    public CustomerService(CustomerRepository customerRepository,
                          CustomerAddressRepository customerAddressRepository) {
        this.customerRepository = customerRepository;
        this.customerAddressRepository = customerAddressRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(String authUserId, String firstName, String lastName,
                                           String email, String phone, LocalDate dateOfBirth) {
        Customer customer = new Customer(authUserId, firstName, lastName, email, phone, dateOfBirth);
        Customer saved = customerRepository.save(customer);
        log.info("customer.profile.created customerId={} outcome=created", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(String customerId, String currentUserId, List<String> roles) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        
        checkOwnerOrStaff(customer, currentUserId, roles);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(String customerId, String currentUserId, List<String> roles,
                                           String firstName, String lastName, String email,
                                           String phone, LocalDate dateOfBirth) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        
        checkOwnerOrAdmin(customer, currentUserId, roles);
        customer.update(firstName, lastName, email, phone, dateOfBirth);
        Customer saved = customerRepository.save(customer);
        log.info("customer.profile.updated customerId={} outcome=updated", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> getAddresses(String customerId, String currentUserId, List<String> roles) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        
        checkOwnerOrStaff(customer, currentUserId, roles);
        
        List<CustomerAddress> addresses = customerAddressRepository.findByCustomerId(customerId);
        return addresses.stream().map(CustomerAddressResponse::from).toList();
    }

    @Transactional
    public CustomerAddressResponse addAddress(String customerId, String currentUserId, List<String> roles,
                                              String type, String line1, String line2,
                                              String city, String state, String postalCode, String country) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        
        checkOwnerOrAdmin(customer, currentUserId, roles);
        
        CustomerAddress address = new CustomerAddress(customerId, type, line1, line2, city, state, postalCode, country);
        CustomerAddress saved = customerAddressRepository.save(address);
        return CustomerAddressResponse.from(saved);
    }

    private void checkOwnerOrAdmin(Customer customer, String currentUserId, List<String> roles) {
        boolean isOwner = customer.getAuthUserId().equals(currentUserId);
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        
        if (!isOwner && !isAdmin) {
            throw new CustomerAccessDeniedException(customer.getId(), currentUserId);
        }
    }

    private void checkOwnerOrStaff(Customer customer, String currentUserId, List<String> roles) {
        boolean isOwner = customer.getAuthUserId().equals(currentUserId);
        boolean isStaff = roles.contains("ROLE_PHARMACIST") || roles.contains("ROLE_STORE_MANAGER") || roles.contains("ROLE_ADMIN");
        
        if (!isOwner && !isStaff) {
            throw new CustomerAccessDeniedException(customer.getId(), currentUserId);
        }
    }
}
