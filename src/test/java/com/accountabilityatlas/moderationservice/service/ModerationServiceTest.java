package com.accountabilityatlas.moderationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.event.ModerationEventPublisher;
import com.accountabilityatlas.moderationservice.exception.ItemAlreadyReviewedException;
import com.accountabilityatlas.moderationservice.exception.ModerationItemNotFoundException;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

  @Mock private ModerationItemRepository moderationItemRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private VideoServiceClient videoServiceClient;
  @Mock private ModerationEventPublisher eventPublisher;
  @Mock private TrustPromotionService trustPromotionService;
  @Mock private TrustDemotionService trustDemotionService;

  private ModerationService moderationService;

  @BeforeEach
  void setUp() {
    moderationService =
        new ModerationService(
            moderationItemRepository,
            auditLogService,
            videoServiceClient,
            eventPublisher,
            trustPromotionService,
            trustDemotionService);
  }

  @Test
  void createItem_validInput_createsItemWithPendingStatus() {
    // Arrange
    UUID contentId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    when(moderationItemRepository.save(any(ModerationItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    ModerationItem result = moderationService.createItem(ContentType.VIDEO, contentId, submitterId);

    // Assert
    assertThat(result.getContentType()).isEqualTo(ContentType.VIDEO);
    assertThat(result.getContentId()).isEqualTo(contentId);
    assertThat(result.getSubmitterId()).isEqualTo(submitterId);
    assertThat(result.getStatus()).isEqualTo(ModerationStatus.PENDING);
  }

  @Test
  void getItem_existingId_returnsItem() {
    // Arrange
    UUID id = UUID.randomUUID();
    ModerationItem item = new ModerationItem();
    item.setId(id);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));

    // Act
    ModerationItem result = moderationService.getItem(id);

    // Assert
    assertThat(result.getId()).isEqualTo(id);
  }

  @Test
  void getItem_nonExistingId_throwsException() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(moderationItemRepository.findById(id)).thenReturn(Optional.empty());

    // Act
    Throwable thrown = catchThrowable(() -> moderationService.getItem(id));

    // Assert
    assertThat(thrown).isInstanceOf(ModerationItemNotFoundException.class);
  }

  @Test
  void approve_pendingItem_setsApprovedStatus() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setContentId(contentId);
    item.setSubmitterId(submitterId);
    item.setStatus(ModerationStatus.PENDING);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(moderationItemRepository.save(any(ModerationItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    ModerationItem result = moderationService.approve(id, reviewerId);

    // Assert
    assertThat(result.getStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(result.getReviewerId()).isEqualTo(reviewerId);
    assertThat(result.getReviewedAt()).isNotNull();
    verify(auditLogService).logAction(reviewerId, "APPROVE", "MODERATION_ITEM", id, null);
    verify(videoServiceClient).updateVideoStatus(contentId, "APPROVED");
    verify(eventPublisher).publishVideoApproved(contentId, reviewerId);
    verify(trustPromotionService).checkAndPromote(submitterId);
  }

  @Test
  void approve_alreadyReviewedItem_throwsException() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setStatus(ModerationStatus.APPROVED);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));

    // Act
    Throwable thrown = catchThrowable(() -> moderationService.approve(id, reviewerId));

    // Assert
    assertThat(thrown).isInstanceOf(ItemAlreadyReviewedException.class);
  }

  @Test
  void reject_pendingItem_setsRejectedStatusWithReason() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String reason = "Off-topic content";
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setContentId(contentId);
    item.setSubmitterId(submitterId);
    item.setStatus(ModerationStatus.PENDING);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(moderationItemRepository.save(any(ModerationItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    ModerationItem result = moderationService.reject(id, reviewerId, reason);

    // Assert
    assertThat(result.getStatus()).isEqualTo(ModerationStatus.REJECTED);
    assertThat(result.getReviewerId()).isEqualTo(reviewerId);
    assertThat(result.getRejectionReason()).isEqualTo(reason);
    verify(auditLogService).logAction(reviewerId, "REJECT", "MODERATION_ITEM", id, reason);
    verify(videoServiceClient).updateVideoStatus(contentId, "REJECTED");
    verify(eventPublisher).publishVideoRejected(contentId, reviewerId, reason);
    verify(trustDemotionService).checkAndDemote(submitterId);
  }

  @Test
  void approve_videoServiceFails_continuesWithoutThrowing() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setContentId(contentId);
    item.setSubmitterId(submitterId);
    item.setStatus(ModerationStatus.PENDING);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(moderationItemRepository.save(any(ModerationItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new VideoServiceException("Connection failed", null))
        .when(videoServiceClient)
        .updateVideoStatus(any(), any());

    // Act
    ModerationItem result = moderationService.approve(id, reviewerId);

    // Assert - approval continues despite video service failure
    assertThat(result.getStatus()).isEqualTo(ModerationStatus.APPROVED);
    verify(eventPublisher).publishVideoApproved(contentId, reviewerId);
    verify(trustPromotionService).checkAndPromote(submitterId);
  }

  @Test
  void reject_videoServiceFails_continuesWithoutThrowing() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String reason = "Inappropriate";
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setContentId(contentId);
    item.setSubmitterId(submitterId);
    item.setStatus(ModerationStatus.PENDING);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));
    when(moderationItemRepository.save(any(ModerationItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new VideoServiceException("Connection failed", null))
        .when(videoServiceClient)
        .updateVideoStatus(any(), any());

    // Act
    ModerationItem result = moderationService.reject(id, reviewerId, reason);

    // Assert - rejection continues despite video service failure
    assertThat(result.getStatus()).isEqualTo(ModerationStatus.REJECTED);
    verify(eventPublisher).publishVideoRejected(contentId, reviewerId, reason);
    verify(trustDemotionService).checkAndDemote(submitterId);
  }

  @Test
  void approve_alreadyReviewedItem_skipsIntegrations() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = new ModerationItem();
    item.setId(id);
    item.setStatus(ModerationStatus.APPROVED);
    when(moderationItemRepository.findById(id)).thenReturn(Optional.of(item));

    // Act
    Throwable thrown = catchThrowable(() -> moderationService.approve(id, reviewerId));

    // Assert
    assertThat(thrown).isInstanceOf(ItemAlreadyReviewedException.class);
    verify(videoServiceClient, never()).updateVideoStatus(any(), any());
    verify(eventPublisher, never()).publishVideoApproved(any(), any());
    verify(trustPromotionService, never()).checkAndPromote(any());
  }
}
