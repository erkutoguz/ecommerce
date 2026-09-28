package dev.erkut.customerservice.security;

public class InvalidCurrentUserException extends RuntimeException {

  public InvalidCurrentUserException(String message) {
    super(message);
  }
}