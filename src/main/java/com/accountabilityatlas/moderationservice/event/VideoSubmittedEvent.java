package com.accountabilityatlas.moderationservice.event;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Event received when a video is submitted by a user.
 *
 * <p>This event is published by video-service when a user submits a new video for review.
 *
 * @param videoId the ID of the submitted video
 * @param submitterId the ID of the user who submitted the video
 * @param submitterTrustTier the trust tier of the submitter (NEW, TRUSTED, MODERATOR, ADMIN)
 * @param title the title of the video
 * @param amendments set of amendment names referenced in the video (e.g., "FIRST", "FOURTH")
 * @param locationIds list of location IDs associated with the video
 * @param timestamp when the submission occurred
 */
public record VideoSubmittedEvent(
    UUID videoId,
    UUID submitterId,
    String submitterTrustTier,
    String title,
    Set<String> amendments,
    List<UUID> locationIds,
    Instant timestamp) {

  /**
   * Checks if the submitter is a new user who requires manual moderation.
   *
   * @return true if the submitter trust tier is NEW
   */
  public boolean requiresModeration() {
    return "NEW".equals(submitterTrustTier);
  }
}
