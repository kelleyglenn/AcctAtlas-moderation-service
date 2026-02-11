package com.accountabilityatlas.moderationservice.event;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

/** Publishes moderation-related events to SQS via Spring Cloud Stream. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationEventPublisher {

  private static final String MODERATION_EVENT_BINDING = "moderationEvent-out-0";

  private final StreamBridge streamBridge;

  /**
   * Publishes a VideoApprovedEvent to the moderation-events queue.
   *
   * @param videoId the ID of the approved video
   * @param reviewerId the ID of the moderator who approved the video
   */
  public void publishVideoApproved(UUID videoId, UUID reviewerId) {
    VideoApprovedEvent event = new VideoApprovedEvent(videoId, reviewerId, Instant.now());
    log.info("Publishing VideoApprovedEvent to SQS: videoId={}", videoId);
    boolean sent = streamBridge.send(MODERATION_EVENT_BINDING, event);
    if (!sent) {
      log.error("Failed to publish VideoApprovedEvent for video {}", videoId);
    }
  }

  /**
   * Publishes a VideoRejectedEvent to the moderation-events queue.
   *
   * @param videoId the ID of the rejected video
   * @param reviewerId the ID of the moderator who rejected the video
   * @param reason the reason for rejection
   */
  public void publishVideoRejected(UUID videoId, UUID reviewerId, String reason) {
    VideoRejectedEvent event = new VideoRejectedEvent(videoId, reviewerId, reason, Instant.now());
    log.info("Publishing VideoRejectedEvent to SQS: videoId={}", videoId);
    boolean sent = streamBridge.send(MODERATION_EVENT_BINDING, event);
    if (!sent) {
      log.error("Failed to publish VideoRejectedEvent for video {}", videoId);
    }
  }
}
