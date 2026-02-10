package com.accountabilityatlas.moderationservice.exception;

import java.util.UUID;

public class ItemAlreadyReviewedException extends RuntimeException {

  public ItemAlreadyReviewedException(UUID id) {
    super("Moderation item already reviewed: " + id);
  }
}
