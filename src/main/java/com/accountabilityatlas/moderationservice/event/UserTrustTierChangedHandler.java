package com.accountabilityatlas.moderationservice.event;

import com.accountabilityatlas.moderationservice.service.ModerationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQS listener for UserTrustTierChangedEvent.
 *
 * <p>When a user is promoted to TRUSTED or higher, auto-approve their pending moderation items.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserTrustTierChangedHandler {

  /** System user ID for auto-approval audit trail. */
  private static final UUID SYSTEM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final ModerationService moderationService;

  /**
   * Handles UserTrustTierChangedEvent from the user-events SQS queue.
   *
   * @param event the user trust tier changed event
   */
  @SqsListener("${app.sqs.user-events-queue:user-events}")
  public void handleUserTrustTierChanged(UserTrustTierChangedEvent event) {
    log.info(
        "Received UserTrustTierChangedEvent from SQS: userId={}, {} -> {}",
        event.userId(),
        event.oldTier(),
        event.newTier());

    if (event.isPromotionToTrusted()) {
      log.info(
          "User {} promoted from NEW to {} - auto-approving pending items",
          event.userId(),
          event.newTier());
      int approved = moderationService.approvePendingItemsForUser(event.userId(), SYSTEM_USER_ID);
      log.info("Auto-approved {} pending items for user {}", approved, event.userId());
    } else {
      log.debug(
          "Trust tier change for user {} ({} -> {}) does not trigger auto-approval",
          event.userId(),
          event.oldTier(),
          event.newTier());
    }
  }
}
