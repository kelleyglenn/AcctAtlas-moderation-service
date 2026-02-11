package com.accountabilityatlas.moderationservice.exception;

import java.util.UUID;

public class ModerationItemNotFoundException extends RuntimeException {

  public ModerationItemNotFoundException(UUID id) {
    super("Moderation item not found: " + id);
  }
}
