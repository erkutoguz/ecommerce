package dev.erkut.customerservice.customer.api.response;

import java.util.UUID;

public record CustomerAddressResponse(
   UUID customerAddressId,
   String fullAddress,
   String city,
   String country
) {}
