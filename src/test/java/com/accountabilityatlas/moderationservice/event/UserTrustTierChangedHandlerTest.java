package com.accountabilityatlas.moderationservice.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.moderationservice.service.ModerationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTrustTierChangedHandlerTest {

  private static final UUID SYSTEM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  @Mock private ModerationService moderationService;

  private UserTrustTierChangedHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UserTrustTierChangedHandler(moderationService);
  }

  @Test
  void handleUserTrustTierChanged_newToTrusted_autoApprovesPendingItems() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserTrustTierChangedEvent event =
        new UserTrustTierChangedEvent(userId, "NEW", "TRUSTED", "AUTO_PROMOTION", Instant.now());
    when(moderationService.approvePendingItemsForUser(userId, SYSTEM_USER_ID)).thenReturn(3);

    // Act
    handler.handleUserTrustTierChanged().accept(event);

    // Assert
    verify(moderationService).approvePendingItemsForUser(userId, SYSTEM_USER_ID);
  }

  @Test
  void handleUserTrustTierChanged_newToModerator_autoApprovesPendingItems() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserTrustTierChangedEvent event =
        new UserTrustTierChangedEvent(userId, "NEW", "MODERATOR", "MANUAL", Instant.now());
    when(moderationService.approvePendingItemsForUser(userId, SYSTEM_USER_ID)).thenReturn(1);

    // Act
    handler.handleUserTrustTierChanged().accept(event);

    // Assert
    verify(moderationService).approvePendingItemsForUser(userId, SYSTEM_USER_ID);
  }

  @Test
  void handleUserTrustTierChanged_trustedToModerator_doesNotAutoApprove() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserTrustTierChangedEvent event =
        new UserTrustTierChangedEvent(userId, "TRUSTED", "MODERATOR", "MANUAL", Instant.now());

    // Act
    handler.handleUserTrustTierChanged().accept(event);

    // Assert
    verify(moderationService, never()).approvePendingItemsForUser(any(), any());
  }

  @Test
  void handleUserTrustTierChanged_trustedToNew_doesNotAutoApprove() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserTrustTierChangedEvent event =
        new UserTrustTierChangedEvent(userId, "TRUSTED", "NEW", "AUTO_DEMOTION", Instant.now());

    // Act
    handler.handleUserTrustTierChanged().accept(event);

    // Assert
    verify(moderationService, never()).approvePendingItemsForUser(any(), any());
  }
}
