package com.accountabilityatlas.moderationservice.web;

import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import com.accountabilityatlas.moderationservice.service.ModerationService.QueueStats;
import com.accountabilityatlas.moderationservice.web.api.QueueApi;
import com.accountabilityatlas.moderationservice.web.model.AddLocationRequest;
import com.accountabilityatlas.moderationservice.web.model.ApproveRequest;
import com.accountabilityatlas.moderationservice.web.model.ModerationItemDetail;
import com.accountabilityatlas.moderationservice.web.model.ModerationQueueResponse;
import com.accountabilityatlas.moderationservice.web.model.QueueStatsResponse;
import com.accountabilityatlas.moderationservice.web.model.RejectRequest;
import com.accountabilityatlas.moderationservice.web.model.UpdateVideoRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModerationQueueController implements QueueApi {

  private final ModerationService moderationService;

  @Override
  public ResponseEntity<ModerationQueueResponse> listModerationQueue(
      @Nullable
          com.accountabilityatlas.moderationservice.web.model.ModerationStatus status,
      @Nullable com.accountabilityatlas.moderationservice.web.model.ContentType contentType,
      String sortBy,
      String direction,
      Integer page,
      Integer size) {

    ModerationStatus domainStatus =
        status != null ? toDomainStatus(status) : ModerationStatus.PENDING;
    ContentType domainContentType = contentType != null ? toDomainContentType(contentType) : null;

    Sort.Direction sortDirection =
        "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
    Sort sort = Sort.by(sortDirection, sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<ModerationItem> queuePage =
        moderationService.getQueue(domainStatus, domainContentType, pageable);

    List<com.accountabilityatlas.moderationservice.web.model.ModerationItem> items =
        queuePage.getContent().stream().map(this::toApiModerationItem).toList();

    ModerationQueueResponse response =
        new ModerationQueueResponse()
            .content(items)
            .page(queuePage.getNumber())
            .size(queuePage.getSize())
            .totalElements((int) queuePage.getTotalElements())
            .totalPages(queuePage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<ModerationItemDetail> getModerationItem(UUID id) {
    ModerationItem item = moderationService.getItem(id);
    return ResponseEntity.ok(toApiModerationItemDetail(item));
  }

  @Override
  public ResponseEntity<ModerationItemDetail> approveContent(
      UUID id, @Nullable ApproveRequest approveRequest) {
    UUID reviewerId = getCurrentUserId();
    ModerationItem item = moderationService.approve(id, reviewerId);
    return ResponseEntity.ok(toApiModerationItemDetail(item));
  }

  @Override
  public ResponseEntity<ModerationItemDetail> rejectContent(UUID id, RejectRequest rejectRequest) {
    UUID reviewerId = getCurrentUserId();
    ModerationItem item = moderationService.reject(id, reviewerId, rejectRequest.getReason());
    return ResponseEntity.ok(toApiModerationItemDetail(item));
  }

  @Override
  public ResponseEntity<QueueStatsResponse> getQueueStats() {
    QueueStats stats = moderationService.getQueueStats();

    QueueStatsResponse response =
        new QueueStatsResponse()
            .pending((int) stats.pending())
            .approvedToday((int) stats.approvedToday())
            .rejectedToday((int) stats.rejectedToday())
            .avgReviewTimeMinutes(
                stats.avgReviewTimeMinutes() != null
                    ? stats.avgReviewTimeMinutes().floatValue()
                    : null);

    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<ModerationItemDetail> addVideoLocation(
      UUID id, AddLocationRequest addLocationRequest) {
    // TODO: Implement video location management via video-service integration
    throw new UnsupportedOperationException("Video location management not yet implemented");
  }

  @Override
  public ResponseEntity<Void> removeVideoLocation(UUID id, UUID locationId) {
    // TODO: Implement video location management via video-service integration
    throw new UnsupportedOperationException("Video location management not yet implemented");
  }

  @Override
  public ResponseEntity<ModerationItemDetail> updateVideoMetadata(
      UUID id, UpdateVideoRequest updateVideoRequest) {
    // TODO: Implement video metadata updates via video-service integration
    throw new UnsupportedOperationException("Video metadata updates not yet implemented");
  }

  private UUID getCurrentUserId() {
    Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String subject = jwt.getSubject();
    return UUID.fromString(subject);
  }

  private ModerationStatus toDomainStatus(
      com.accountabilityatlas.moderationservice.web.model.ModerationStatus apiStatus) {
    return switch (apiStatus) {
      case PENDING -> ModerationStatus.PENDING;
      case APPROVED -> ModerationStatus.APPROVED;
      case REJECTED -> ModerationStatus.REJECTED;
    };
  }

  private ContentType toDomainContentType(
      com.accountabilityatlas.moderationservice.web.model.ContentType apiContentType) {
    return switch (apiContentType) {
      case VIDEO -> ContentType.VIDEO;
      case LOCATION -> ContentType.LOCATION;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.ModerationStatus toApiStatus(
      ModerationStatus domainStatus) {
    return switch (domainStatus) {
      case PENDING -> com.accountabilityatlas.moderationservice.web.model.ModerationStatus.PENDING;
      case APPROVED ->
          com.accountabilityatlas.moderationservice.web.model.ModerationStatus.APPROVED;
      case REJECTED ->
          com.accountabilityatlas.moderationservice.web.model.ModerationStatus.REJECTED;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.ContentType toApiContentType(
      ContentType domainContentType) {
    return switch (domainContentType) {
      case VIDEO -> com.accountabilityatlas.moderationservice.web.model.ContentType.VIDEO;
      case LOCATION -> com.accountabilityatlas.moderationservice.web.model.ContentType.LOCATION;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.ModerationItem toApiModerationItem(
      ModerationItem item) {
    return new com.accountabilityatlas.moderationservice.web.model.ModerationItem()
        .id(item.getId())
        .contentType(toApiContentType(item.getContentType()))
        .contentId(item.getContentId())
        .submitterId(item.getSubmitterId())
        .status(toApiStatus(item.getStatus()))
        .priority(item.getPriority())
        .reviewerId(item.getReviewerId())
        .reviewedAt(toOffsetDateTime(item.getReviewedAt()))
        .rejectionReason(item.getRejectionReason())
        .createdAt(toOffsetDateTime(item.getCreatedAt()));
  }

  private ModerationItemDetail toApiModerationItemDetail(ModerationItem item) {
    return new ModerationItemDetail()
        .id(item.getId())
        .contentType(toApiContentType(item.getContentType()))
        .contentId(item.getContentId())
        .submitterId(item.getSubmitterId())
        .status(toApiStatus(item.getStatus()))
        .priority(item.getPriority())
        .reviewerId(item.getReviewerId())
        .reviewedAt(toOffsetDateTime(item.getReviewedAt()))
        .rejectionReason(item.getRejectionReason())
        .createdAt(toOffsetDateTime(item.getCreatedAt()));
    // Note: submitter, reviewer, and contentPreview would be populated
    // by enriching with data from user-service and video-service
  }

  @Nullable
  private OffsetDateTime toOffsetDateTime(@Nullable java.time.Instant instant) {
    if (instant == null) {
      return null;
    }
    return instant.atOffset(ZoneOffset.UTC);
  }
}
