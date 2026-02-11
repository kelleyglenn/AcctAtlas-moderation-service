package com.accountabilityatlas.moderationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.moderationservice.client.UserServiceClient;
import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserStats;
import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserSummary;
import com.accountabilityatlas.moderationservice.repository.AbuseReportRepository;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrustPromotionServiceTest {

  @Mock private UserServiceClient userServiceClient;
  @Mock private ModerationItemRepository moderationItemRepository;
  @Mock private AbuseReportRepository abuseReportRepository;

  private TrustPromotionService trustPromotionService;

  @BeforeEach
  void setUp() {
    trustPromotionService =
        new TrustPromotionService(
            userServiceClient, moderationItemRepository, abuseReportRepository);
  }

  @Test
  void checkAndPromote_userNotNew_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED", 60, 15);
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_accountTooNew_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 15, 15); // Only 15 days old
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_notEnoughApprovals_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 60, 5); // Only 5 approvals
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_hasRecentRejections_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 60, 15);
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(any(UUID.class), any(Instant.class)))
        .thenReturn(1);

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_hasActiveReports_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 60, 15);
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(any(UUID.class), any(Instant.class)))
        .thenReturn(0);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(1);

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_allCriteriaMet_promotesAndReturnsTrue() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 60, 15);
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(any(UUID.class), any(Instant.class)))
        .thenReturn(0);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(0);

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "TRUSTED", "AUTO_PROMOTION");
  }

  @Test
  void checkAndPromote_userNotFound_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    when(userServiceClient.getUser(userId)).thenReturn(Optional.empty());

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndPromote_exactlyAtThresholds_promotesAndReturnsTrue() {
    // Arrange - test boundary conditions: exactly 30 days old, exactly 10 approvals
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW", 30, 10);
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(any(UUID.class), any(Instant.class)))
        .thenReturn(0);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(0);

    // Act
    boolean result = trustPromotionService.checkAndPromote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "TRUSTED", "AUTO_PROMOTION");
  }

  private UserSummary createUser(
      UUID userId, String trustTier, int accountAgeDays, int approvedCount) {
    Instant createdAt = Instant.now().minus(accountAgeDays, ChronoUnit.DAYS);
    UserStats stats = new UserStats(approvedCount + 2, approvedCount); // submissionCount > approved
    return new UserSummary(userId, "testuser", null, trustTier, stats, createdAt);
  }
}
