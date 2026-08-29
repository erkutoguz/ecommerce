package dev.erkut.productservice.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @Size(max = 255)
        String name,

        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal price
) {}
