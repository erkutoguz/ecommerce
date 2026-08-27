package dev.erkut.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomerAddressCreateRequest(
        @NotNull
        UUID customerId,

        @NotBlank
        String fullAddress,

        @NotBlank
        String city,

        @NotBlank
        String country
) {}
