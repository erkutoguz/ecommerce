package dev.erkut.customerservice.model;

import dev.erkut.customerservice.exception.AddressNotFoundException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @OneToMany(
            mappedBy = "customer",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CustomerAddress> addresses = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {}

    private Customer(String name, String email, String phone, Instant now) {
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        if(email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null");
        }

        if(now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.name = name;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone;
        this.status = CustomerStatus.ACTIVE;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public static Customer create(String name, String email, String phone, Instant now) {
        return new Customer(name, email, phone, now);
    }

    public void addAddress(String fullAddress, String city, String country, Instant now) {
        ensureCustomerEditable();
        validateUpdateTime(now);
        CustomerAddress address = CustomerAddress.create(this, fullAddress, city, country);
        addresses.add(address);
        updateTime(now);
    }

    public void removeAddress(UUID addressId, Instant now) {
        ensureCustomerEditable();
        validateUpdateTime(now);
        CustomerAddress address = findAddress(addressId);
        addresses.remove(address);
        updateTime(now);
    }

    public void makeCustomerInactive(Instant now) {
        if(status == CustomerStatus.INACTIVE) {
            return;
        }
        validateUpdateTime(now);
        ensureCustomerEditable();

        status = CustomerStatus.INACTIVE;
        updateTime(now);
    }

    private void validateUpdateTime(Instant now) {
        if(now == null) {
            throw new IllegalArgumentException("Update time cannot be null");
        }
    }

    private void ensureCustomerEditable() {
        if (status != CustomerStatus.ACTIVE) {
            throw new IllegalStateException("Customer cannot be modified with status " + status);
        }
    }

    private void updateTime(Instant now) {
        this.updatedAt = now;
    }

    private CustomerAddress findAddress(UUID addressId) {
        if (addressId == null) {
            throw new IllegalArgumentException("Address id cannot be null");
        }

        return addresses.stream()
                .filter(address -> address.hasId(addressId))
                .findFirst()
                .orElseThrow(() ->
                        new AddressNotFoundException("Address does not exist in customer: " + addressId)
                );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public List<CustomerAddress> getAddresses() {
        return List.copyOf(addresses);
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
