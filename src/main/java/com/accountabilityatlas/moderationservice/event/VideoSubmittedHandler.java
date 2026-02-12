package com.accountabilityatlas.moderationservice.event;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQS listener for VideoSubmittedEvent.
 *
 * <p>When a video is submitted:
 *
 * <ul>
 *   <li>NEW users: Video is queued for manual moderation
 *   <li>TRUSTED, MODERATOR, ADMIN users: Video is auto-approved
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoSubmittedHandler {

  private final ModerationService moderationService;
  private final VideoServiceClient videoServiceClient;
  private final ModerationEventPublisher moderationEventPublisher;

  /**
   * Handles VideoSubmittedEvent from SQS.
   *
   * @param event the video submitted event
   */
  @SqsListener("${app.sqs.video-events-queue:video-events}")
  public void handleVideoSubmitted(VideoSubmittedEvent event) {
    log.info(
        "Received VideoSubmittedEvent from SQS: videoId={}, submitterId={}, trustTier={}",
        event.videoId(),
        event.submitterId(),
        event.submitterTrustTier());

    if (event.requiresModeration()) {
      log.info(
          "Queuing video {} for moderation (submitter {} has trust tier NEW)",
          event.videoId(),
          event.submitterId());
      moderationService.createItem(ContentType.VIDEO, event.videoId(), event.submitterId());
    } else {
      log.info(
          "Auto-approving video {} (submitter {} has trust tier {})",
          event.videoId(),
          event.submitterId(),
          event.submitterTrustTier());
      videoServiceClient.updateVideoStatus(event.videoId(), "APPROVED");
      moderationEventPublisher.publishVideoApproved(event.videoId(), event.submitterId());
    }
  }
}
