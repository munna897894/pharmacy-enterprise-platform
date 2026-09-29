package com.jagapathi.pharmacy.customer.domain.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "customer_address", indexes = {
        @Index(name = "idx_customer_id", columnList = "customer_id")
})
public class CustomerAddress {

    @Id
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "line1", nullable = false, length = 255)
    private String line1;

    @Column(name = "line2", length = 255)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 50)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    protected CustomerAddress() {}

    public CustomerAddress(String customerId, String type, String line1, String line2,
                          String city, String state, String postalCode, String country) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.type = type;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getType() { return type; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
}
