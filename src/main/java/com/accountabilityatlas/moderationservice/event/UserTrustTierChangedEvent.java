package com.accountabilityatlas.moderationservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received when a user's trust tier changes.
 *
 * @param userId the user whose tier changed
 * @param oldTier the previous trust tier
 * @param newTier the new trust tier
 * @param reason the reason for the change
 * @param timestamp when the change occurred
 */
public record UserTrustTierChangedEvent(
    UUID userId, String oldTier, String newTier, String reason, Instant timestamp) {

  /**
   * Checks if the user was promoted to a trusted tier (TRUSTED, MODERATOR, or ADMIN).
   *
   * @return true if the new tier is trusted
   */
  public boolean isPromotionToTrusted() {
    return "NEW".equals(oldTier)
        && ("TRUSTED".equals(newTier) || "MODERATOR".equals(newTier) || "ADMIN".equals(newTier));
  }
}
