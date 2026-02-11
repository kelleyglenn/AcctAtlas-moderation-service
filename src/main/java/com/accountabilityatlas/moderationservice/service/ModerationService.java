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
    auditLogService.logAction(reviewerId, "APPROVE", "MODERATION_ITEM", id, null);
    ModerationItem saved = moderationItemRepository.save(item);

    // Update video status in video-service
    try {
      videoServiceClient.updateVideoStatus(item.getContentId(), "APPROVED");
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
    auditLogService.logAction(reviewerId, "REJECT", "MODERATION_ITEM", id, reason);
    ModerationItem saved = moderationItemRepository.save(item);

    // Update video status in video-service
    try {
      videoServiceClient.updateVideoStatus(item.getContentId(), "REJECTED");
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

  private ModerationItem getItemInternal(UUID id) {
    return moderationItemRepository
        .findById(id)
        .orElseThrow(() -> new ModerationItemNotFoundException(id));
  }

  public record QueueStats(
      long pending, long approvedToday, long rejectedToday, Double avgReviewTimeMinutes) {}
}
