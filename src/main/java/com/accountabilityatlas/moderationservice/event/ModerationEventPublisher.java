package com.accountabilityatlas.moderationservice.event;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes moderation-related events using Spring's ApplicationEventPublisher.
 *
 * <p>This is a local development solution that publishes events within the application context. In
 * production, events will be published to SQS for inter-service communication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  /**
   * Publishes a VideoApprovedEvent.
   *
   * @param videoId the ID of the approved video
   * @param reviewerId the ID of the moderator who approved the video
   */
  public void publishVideoApproved(UUID videoId, UUID reviewerId) {
    VideoApprovedEvent event = new VideoApprovedEvent(videoId, reviewerId, Instant.now());
    log.debug("Publishing VideoApprovedEvent: videoId={}, reviewerId={}", videoId, reviewerId);
    applicationEventPublisher.publishEvent(event);
    log.info("Published VideoApprovedEvent for video {}", videoId);
  }

  /**
   * Publishes a VideoRejectedEvent.
   *
   * @param videoId the ID of the rejected video
   * @param reviewerId the ID of the moderator who rejected the video
   * @param reason the reason for rejection
   */
  public void publishVideoRejected(UUID videoId, UUID reviewerId, String reason) {
    VideoRejectedEvent event = new VideoRejectedEvent(videoId, reviewerId, reason, Instant.now());
    log.debug(
        "Publishing VideoRejectedEvent: videoId={}, reviewerId={}, reason={}",
        videoId,
        reviewerId,
        reason);
    applicationEventPublisher.publishEvent(event);
    log.info("Published VideoRejectedEvent for video {}", videoId);
  }
}
