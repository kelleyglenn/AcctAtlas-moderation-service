package com.accountabilityatlas.moderationservice.event;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Publishes moderation-related events to SQS via AWS Spring SqsTemplate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationEventPublisher {

  private final SqsTemplate sqsTemplate;

  @Value("${app.sqs.moderation-events-queue:moderation-events}")
  private String moderationEventsQueue;

  /**
   * Publishes a VideoApprovedEvent to the moderation-events queue.
   *
   * @param videoId the ID of the approved video
   * @param reviewerId the ID of the moderator who approved the video
   */
  public void publishVideoApproved(UUID videoId, UUID reviewerId) {
    VideoApprovedEvent event = new VideoApprovedEvent(videoId, reviewerId, Instant.now());
    log.info(
        "Publishing VideoApprovedEvent to SQS queue {}: videoId={}",
        moderationEventsQueue,
        videoId);
    try {
      sqsTemplate.send(moderationEventsQueue, event);
      log.debug("Published VideoApprovedEvent successfully");
    } catch (Exception e) {
      log.error(
          "Failed to publish VideoApprovedEvent for video {}: {}", videoId, e.getMessage(), e);
      throw e;
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
    log.info(
        "Publishing VideoRejectedEvent to SQS queue {}: videoId={}",
        moderationEventsQueue,
        videoId);
    try {
      sqsTemplate.send(moderationEventsQueue, event);
      log.debug("Published VideoRejectedEvent successfully");
    } catch (Exception e) {
      log.error(
          "Failed to publish VideoRejectedEvent for video {}: {}", videoId, e.getMessage(), e);
      throw e;
    }
  }
}
