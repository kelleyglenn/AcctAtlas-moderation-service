package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.exception.ItemAlreadyReviewedException;
import com.accountabilityatlas.moderationservice.exception.ModerationItemNotFoundException;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModerationService {

  private final ModerationItemRepository moderationItemRepository;
  private final AuditLogService auditLogService;

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
  public Page<ModerationItem> getQueue(ModerationStatus status, Pageable pageable) {
    return moderationItemRepository.findByStatus(status, pageable);
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
    return moderationItemRepository.save(item);
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
    return moderationItemRepository.save(item);
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
