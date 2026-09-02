package dev.erkut.customerservice.customer.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "full_address", nullable = false, length = 255)
    private String fullAddress;

    @Column(name = "city", nullable = false, length = 50)
    private String city;

    @Column(name = "country", nullable = false, length = 50)
    private String country;

    protected CustomerAddress(){}

    private CustomerAddress(Customer customer, String fullAddress, String city, String country) {
        if(customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }

        if(fullAddress == null || fullAddress.isBlank()) {
            throw new IllegalArgumentException("Full address cannot be null");
        }

        if(city == null || city.isBlank()) {
            throw new IllegalArgumentException("City cannot be null");
        }

        if(country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country cannot be null");
        }

        this.customer = customer;
        this.fullAddress = fullAddress;
        this.city = city;
        this.country = country;
    }

    static CustomerAddress create(Customer customer, String fullAddress, String city, String country) {
        return new CustomerAddress(customer, fullAddress, city, country);
    }

    boolean hasId(UUID addressId) {
        return addressId.equals(id);
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
