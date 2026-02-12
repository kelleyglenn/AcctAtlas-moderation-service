package com.accountabilityatlas.moderationservice.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoSubmittedHandlerTest {

  @Mock private ModerationService moderationService;
  @Mock private VideoServiceClient videoServiceClient;
  @Mock private ModerationEventPublisher moderationEventPublisher;

  private VideoSubmittedHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new VideoSubmittedHandler(moderationService, videoServiceClient, moderationEventPublisher);
  }

  @Test
  void handleVideoSubmitted_newUser_createsModerationItem() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    VideoSubmittedEvent event =
        new VideoSubmittedEvent(
            videoId,
            submitterId,
            "NEW",
            "Test Video",
            Set.of("FIRST", "FOURTH"),
            List.of(UUID.randomUUID()),
            Instant.now());

    ModerationItem item = new ModerationItem();
    item.setId(UUID.randomUUID());
    when(moderationService.createItem(ContentType.VIDEO, videoId, submitterId)).thenReturn(item);

    // Act
    handler.handleVideoSubmitted(event);

    // Assert
    verify(moderationService).createItem(ContentType.VIDEO, videoId, submitterId);
    verify(videoServiceClient, never()).updateVideoStatus(any(), any());
    verify(moderationEventPublisher, never()).publishVideoApproved(any(), any());
  }

  @ParameterizedTest(name = "trust tier {0} auto-approves")
  @ValueSource(strings = {"TRUSTED", "MODERATOR", "ADMIN"})
  void handleVideoSubmitted_trustedTiers_autoApproves(String trustTier) {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID submitterId = UUID.randomUUID();
    VideoSubmittedEvent event =
        new VideoSubmittedEvent(
            videoId,
            submitterId,
            trustTier,
            "Test Video",
            Set.of("FIRST", "FOURTH"),
            List.of(UUID.randomUUID()),
            Instant.now());

    // Act
    handler.handleVideoSubmitted(event);

    // Assert
    verify(moderationService, never()).createItem(any(), any(), any());
    verify(videoServiceClient).updateVideoStatus(videoId, "APPROVED");
    verify(moderationEventPublisher).publishVideoApproved(videoId, submitterId);
  }
}
