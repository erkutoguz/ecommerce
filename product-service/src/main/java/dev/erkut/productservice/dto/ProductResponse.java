package dev.erkut.productservice.dto;

import dev.erkut.productservice.model.ProductStatus;

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
