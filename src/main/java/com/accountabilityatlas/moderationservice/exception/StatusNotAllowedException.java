package com.accountabilityatlas.moderationservice.exception;

import com.accountabilityatlas.moderationservice.domain.ModerationStatus;

/**
 * Exception thrown when a user attempts to modify a moderation item with a status they are not
 * allowed to modify.
 */
public class StatusNotAllowedException extends RuntimeException {

  public StatusNotAllowedException(ModerationStatus status) {
    super("Moderators can only modify PENDING videos. Current status: " + status);
  }
}
