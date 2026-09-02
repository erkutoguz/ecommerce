package dev.erkut.productservice.product.api.response;

import dev.erkut.productservice.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse (
  UUID productId,
  String name,
  BigDecimal price,
  ProductStatus status,
  Instant updatedAt,
  Instant createdAt
) {}
