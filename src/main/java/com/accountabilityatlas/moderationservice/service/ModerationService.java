package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.event.ModerationEventPublisher;
import com.accountabilityatlas.moderationservice.exception.ItemAlreadyReviewedException;
import com.accountabilityatlas.moderationservice.exception.ModerationItemNotFoundException;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

  public static final String MODERATION_ITEM = "MODERATION_ITEM";
  public static final String ACTION_APPROVE = "APPROVE";
  public static final String ACTION_REJECT = "REJECT";
  public static final String ACTION_AUTO_APPROVE = "AUTO_APPROVE";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_REJECTED = "REJECTED";
  private final ModerationItemRepository moderationItemRepository;
  private final AuditLogService auditLogService;
  private final VideoServiceClient videoServiceClient;
  private final ModerationEventPublisher eventPublisher;
  private final TrustPromotionService trustPromotionService;
  private final TrustDemotionService trustDemotionService;

  @Transactional
  public ModerationItem createItem(ContentType contentType, UUID contentId, UUID submitterId) {
    ModerationItem item = new ModerationItem();
    item.setContentType(contentType);
    item.setContentId(contentId);
    item.setSubmitterId(submitterId);
    item.setStatus(ModerationStatus.PENDING);
    item.setPriority(0);
    return moderationItemRepository.save(item);
  }

  @Transactional(readOnly = true)
  public ModerationItem getItem(UUID id) {
    return getItemInternal(id);
  }

  @Transactional(readOnly = true)
  public Page<ModerationItem> getQueue(
      ModerationStatus status, @Nullable ContentType contentType, Pageable pageable) {
    if (contentType != null) {
      return moderationItemRepository.findByStatusAndContentType(status, contentType, pageable);
    }
    return moderationItemRepository.findByStatus(status, pageable);
  }

  @Transactional
  public ModerationItem approve(UUID id, UUID reviewerId) {
    ModerationItem item = getItemInternal(id);
    if (item.getStatus() != ModerationStatus.PENDING) {
      throw new ItemAlreadyReviewedException(id);
    }
    item.setStatus(ModerationStatus.APPROVED);
    item.setReviewerId(reviewerId);
    item.setReviewedAt(Instant.now());
    auditLogService.logAction(reviewerId, ACTION_APPROVE, MODERATION_ITEM, id, null);
    ModerationItem saved = moderationItemRepository.save(item);

    // Update video status in video-service
    try {
      videoServiceClient.updateVideoStatus(item.getContentId(), STATUS_APPROVED);
    } catch (Exception e) {
      log.error(
          "Failed to update video {} status to APPROVED: {}", item.getContentId(), e.getMessage());
    }

    // Publish approval event
    eventPublisher.publishVideoApproved(item.getContentId(), reviewerId);

    // Check if submitter qualifies for trust tier promotion
    try {
      boolean promoted = trustPromotionService.checkAndPromote(item.getSubmitterId());
      if (promoted) {
        log.info("User {} was promoted after approval of video {}", item.getSubmitterId(), id);
      }
    } catch (Exception e) {
      log.error(
          "Failed to check trust promotion for user {}: {}", item.getSubmitterId(), e.getMessage());
    }

    return saved;
  }

  @Transactional
  public ModerationItem reject(UUID id, UUID reviewerId, String reason) {
    ModerationItem item = getItemInternal(id);
    if (item.getStatus() != ModerationStatus.PENDING) {
      throw new ItemAlreadyReviewedException(id);
    }
    item.setStatus(ModerationStatus.REJECTED);
    item.setReviewerId(reviewerId);
    item.setReviewedAt(Instant.now());
    item.setRejectionReason(reason);
    auditLogService.logAction(reviewerId, ACTION_REJECT, MODERATION_ITEM, id, reason);
    ModerationItem saved = moderationItemRepository.save(item);

    // Update video status in video-service
    try {
      videoServiceClient.updateVideoStatus(item.getContentId(), STATUS_REJECTED);
    } catch (Exception e) {
      log.error(
          "Failed to update video {} status to REJECTED: {}", item.getContentId(), e.getMessage());
    }

    // Publish rejection event
    eventPublisher.publishVideoRejected(item.getContentId(), reviewerId, reason);

    // Check if submitter should be demoted
    try {
      boolean demoted = trustDemotionService.checkAndDemote(item.getSubmitterId());
      if (demoted) {
        log.info("User {} was demoted after rejection of video {}", item.getSubmitterId(), id);
      }
    } catch (Exception e) {
      log.error(
          "Failed to check trust demotion for user {}: {}", item.getSubmitterId(), e.getMessage());
    }

    return saved;
  }

  @Transactional(readOnly = true)
  public QueueStats getQueueStats() {
    long pending = moderationItemRepository.countByStatus(ModerationStatus.PENDING);
    Instant startOfDay = LocalDate.now(ZoneId.of("UTC")).atStartOfDay().toInstant(ZoneOffset.UTC);

    long approvedToday =
        moderationItemRepository.countByStatusAndReviewedAtGreaterThanEqual(
            ModerationStatus.APPROVED, startOfDay);
    long rejectedToday =
        moderationItemRepository.countByStatusAndReviewedAtGreaterThanEqual(
            ModerationStatus.REJECTED, startOfDay);
    Double avgReviewTime = moderationItemRepository.calculateAverageReviewTimeMinutes();

    return new QueueStats(pending, approvedToday, rejectedToday, avgReviewTime);
  }

  /**
   * Auto-approves all pending moderation items for a user.
   *
   * <p>Called when a user's trust tier is upgraded to TRUSTED or higher.
   *
   * @param submitterId the user whose pending items should be approved
   * @param systemReviewerId the system user ID for audit purposes
   * @return the number of items approved
   */
  @Transactional
  public int approvePendingItemsForUser(UUID submitterId, UUID systemReviewerId) {
    List<ModerationItem> pendingItems =
        moderationItemRepository.findBySubmitterIdAndStatus(submitterId, ModerationStatus.PENDING);

    if (pendingItems.isEmpty()) {
      log.debug("No pending items found for user {}", submitterId);
      return 0;
    }

    log.info(
        "Auto-approving {} pending items for user {} (trust tier upgraded)",
        pendingItems.size(),
        submitterId);

    int approved = 0;
    for (ModerationItem item : pendingItems) {
      try {
        item.setStatus(ModerationStatus.APPROVED);
        item.setReviewerId(systemReviewerId);
        item.setReviewedAt(Instant.now());
        moderationItemRepository.save(item);

        // Update video status
        videoServiceClient.updateVideoStatus(item.getContentId(), STATUS_APPROVED);

        // Publish approval event
        eventPublisher.publishVideoApproved(item.getContentId(), systemReviewerId);

        auditLogService.logAction(
            systemReviewerId, ACTION_AUTO_APPROVE, MODERATION_ITEM, item.getId(), "trust_tier_upgrade");
        approved++;
      } catch (Exception e) {
        log.error("Failed to auto-approve item {}: {}", item.getId(), e.getMessage());
      }
    }

    return approved;
  }

  private ModerationItem getItemInternal(UUID id) {
    return moderationItemRepository
        .findById(id)
        .orElseThrow(() -> new ModerationItemNotFoundException(id));
  }

  public record QueueStats(
      long pending, long approvedToday, long rejectedToday, Double avgReviewTimeMinutes) {}
}
