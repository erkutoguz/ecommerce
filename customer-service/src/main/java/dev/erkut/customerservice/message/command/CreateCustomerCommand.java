package dev.erkut.customerservice.message.command;

import java.util.UUID;

public record CreateCustomerCommand(
   UUID authUserId,
   String email
) {}
