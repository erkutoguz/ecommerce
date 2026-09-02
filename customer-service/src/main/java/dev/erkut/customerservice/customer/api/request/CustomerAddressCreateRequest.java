package dev.erkut.customerservice.customer.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerAddressCreateRequest(
        @NotBlank
        @Size(max = 255)
        String fullAddress,

        @NotBlank
        @Size(max = 50)
        String city,

        @NotBlank
        @Size(max = 50)
        String country
) {}
