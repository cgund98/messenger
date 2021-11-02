package com.github.cgund98.messenger.exceptions;

/** Custom exception type for use when an item does not exist in a database. */
public class NotFoundException extends Exception {
  public NotFoundException(String message) {
    super(message);
  }
}
