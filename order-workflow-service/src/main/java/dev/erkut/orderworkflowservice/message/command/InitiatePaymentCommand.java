package dev.erkut.orderworkflowservice.message.command;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePaymentCommand(
   UUID orderId,
   BigDecimal totalAmount,
   Currency currency
) {}
