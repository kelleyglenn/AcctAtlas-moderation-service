package com.accountabilityatlas.moderationservice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a video is approved during moderation.
 *
 * @param videoId the ID of the approved video
 * @param reviewerId the ID of the moderator who approved the video
 * @param timestamp when the approval occurred
 */
public record VideoApprovedEvent(UUID videoId, UUID reviewerId, Instant timestamp) {}
