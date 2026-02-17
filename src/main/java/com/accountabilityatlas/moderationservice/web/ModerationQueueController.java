package com.accountabilityatlas.moderationservice.web;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.exception.StatusNotAllowedException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModerationQueueController implements QueueApi {

  private final ModerationService moderationService;
  private final VideoServiceClient videoServiceClient;

  @Override
  public ResponseEntity<ModerationQueueResponse> listModerationQueue(
      @Nullable com.accountabilityatlas.moderationservice.web.model.ModerationStatus status,
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
  public ResponseEntity<com.accountabilityatlas.moderationservice.web.model.ModerationItem>
      getModerationItemByContentId(
          UUID contentId,
          com.accountabilityatlas.moderationservice.web.model.ModerationStatus status) {
    ModerationStatus domainStatus =
        status != null ? ModerationStatus.valueOf(status.name()) : ModerationStatus.PENDING;
    return moderationService
        .findByContentId(contentId, domainStatus)
        .map(item -> ResponseEntity.ok(toApiModerationItem(item)))
        .orElse(ResponseEntity.notFound().build());
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
    ModerationItem item = moderationService.getItem(id);
    checkVideoModificationAllowed(item);

    boolean isPrimary =
        addLocationRequest.getIsPrimary() != null && addLocationRequest.getIsPrimary();
    videoServiceClient.addLocation(
        item.getContentId(), addLocationRequest.getLocationId(), isPrimary);

    return ResponseEntity.ok(toApiModerationItemDetail(item));
  }

  @Override
  public ResponseEntity<Void> removeVideoLocation(UUID id, UUID locationId) {
    ModerationItem item = moderationService.getItem(id);
    checkVideoModificationAllowed(item);

    videoServiceClient.removeLocation(item.getContentId(), locationId);

    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ModerationItemDetail> updateVideoMetadata(
      UUID id, UpdateVideoRequest updateVideoRequest) {
    ModerationItem item = moderationService.getItem(id);
    checkVideoModificationAllowed(item);

    // Convert API model to client request
    List<String> amendments =
        updateVideoRequest.getAmendments() != null
            ? updateVideoRequest.getAmendments().stream()
                .map(UpdateVideoRequest.AmendmentsEnum::getValue)
                .toList()
            : null;
    List<String> participants =
        updateVideoRequest.getParticipants() != null
            ? updateVideoRequest.getParticipants().stream()
                .map(UpdateVideoRequest.ParticipantsEnum::getValue)
                .toList()
            : null;

    VideoServiceClient.UpdateVideoMetadataRequest clientRequest =
        new VideoServiceClient.UpdateVideoMetadataRequest(
            amendments, participants, updateVideoRequest.getVideoDate());

    videoServiceClient.updateVideoMetadata(item.getContentId(), clientRequest);

    return ResponseEntity.ok(toApiModerationItemDetail(item));
  }

  /**
   * Checks if the current user is allowed to modify the video based on item status.
   *
   * <p>Moderators can only modify PENDING videos. Admins can modify videos with any status.
   *
   * @param item the moderation item
   * @throws StatusNotAllowedException if the user is not allowed to modify this item
   */
  private void checkVideoModificationAllowed(ModerationItem item) {
    if (item.getStatus() != ModerationStatus.PENDING && !isAdmin()) {
      throw new StatusNotAllowedException(item.getStatus());
    }
  }

  /**
   * Checks if the current user has the ADMIN role.
   *
   * @return true if the user is an admin
   */
  private boolean isAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(auth -> auth.equals("ROLE_ADMIN"));
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
