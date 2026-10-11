package dev.erkut.stockservice.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCreatedEvent(
   UUID productId
) {}
