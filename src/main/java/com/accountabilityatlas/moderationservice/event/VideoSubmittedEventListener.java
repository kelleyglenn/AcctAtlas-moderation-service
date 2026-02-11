package com.accountabilityatlas.moderationservice.event;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for VideoSubmittedEvent and handles moderation workflow.
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
public class VideoSubmittedEventListener {

  private final ModerationService moderationService;
  private final VideoServiceClient videoServiceClient;
  private final ModerationEventPublisher moderationEventPublisher;

  /**
   * Handles a video submission event.
   *
   * @param event the video submitted event
   */
  @EventListener
  @Transactional
  public void handleVideoSubmitted(VideoSubmittedEvent event) {
    log.info(
        "Received VideoSubmittedEvent: videoId={}, submitterId={}, trustTier={}",
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
