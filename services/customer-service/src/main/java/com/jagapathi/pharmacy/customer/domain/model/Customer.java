package com.jagapathi.pharmacy.customer.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer", indexes = {
        @Index(name = "idx_auth_user_id", columnList = "auth_user_id"),
        @Index(name = "idx_email", columnList = "email")
})
public class Customer {

    @Id
    private String id;

    @Column(name = "auth_user_id", nullable = false, unique = true)
    private String authUserId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    protected Customer() {}

    public Customer(String authUserId, String firstName, String lastName, String email,
                   String phone, LocalDate dateOfBirth) {
        this.id = UUID.randomUUID().toString();
        this.authUserId = authUserId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        // Leave version null so Spring Data JPA's isNew() check (version == null)
        // routes brand-new entities through persist() instead of merge(); Hibernate
        // initializes @Version to 0 on the actual insert.
    }

    public void update(String firstName, String lastName, String email, String phone, LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAuthUserId() { return authUserId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
