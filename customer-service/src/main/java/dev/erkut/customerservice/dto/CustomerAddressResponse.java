package dev.erkut.customerservice.dto;

import java.util.UUID;

public record CustomerAddressResponse(
   UUID customerAddressId,
   String fullAddress,
   String city,
   String country
) {}
