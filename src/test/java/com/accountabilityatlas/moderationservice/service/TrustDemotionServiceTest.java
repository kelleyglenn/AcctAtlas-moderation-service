package com.accountabilityatlas.moderationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class TrustDemotionServiceTest {

  @Mock private UserServiceClient userServiceClient;
  @Mock private ModerationItemRepository moderationItemRepository;
  @Mock private AbuseReportRepository abuseReportRepository;

  private TrustDemotionService trustDemotionService;

  @BeforeEach
  void setUp() {
    trustDemotionService =
        new TrustDemotionService(
            userServiceClient, moderationItemRepository, abuseReportRepository);
  }

  @Test
  void checkAndDemote_userNotTrusted_returnsFalse() {
    // Arrange - user is NEW, not TRUSTED
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "NEW");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndDemote_moderatorNotDemoted_returnsFalse() {
    // Arrange - MODERATOR should not be auto-demoted
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "MODERATOR");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndDemote_adminNotDemoted_returnsFalse() {
    // Arrange - ADMIN should not be auto-demoted
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "ADMIN");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndDemote_noViolations_returnsFalse() {
    // Arrange - TRUSTED user with < 3 rejections AND < 3 reports
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(2); // Less than 3
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(2); // Less than 3

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  @Test
  void checkAndDemote_threeRejections_demotesAndReturnsTrue() {
    // Arrange - TRUSTED user with exactly 3 rejections
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(3);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(0);

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "NEW", "AUTO_DEMOTION");
  }

  @Test
  void checkAndDemote_moreThanThreeRejections_demotesAndReturnsTrue() {
    // Arrange - TRUSTED user with more than 3 rejections
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(5);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(0);

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "NEW", "AUTO_DEMOTION");
  }

  @Test
  void checkAndDemote_threeActiveReports_demotesAndReturnsTrue() {
    // Arrange - TRUSTED user with exactly 3 active reports
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(0);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(3);

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "NEW", "AUTO_DEMOTION");
  }

  @Test
  void checkAndDemote_moreThanThreeActiveReports_demotesAndReturnsTrue() {
    // Arrange - TRUSTED user with more than 3 active reports
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(0);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(7);

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "NEW", "AUTO_DEMOTION");
  }

  @Test
  void checkAndDemote_bothConditions_demotesAndReturnsTrue() {
    // Arrange - TRUSTED user with both >= 3 rejections AND >= 3 reports
    UUID userId = UUID.randomUUID();
    UserSummary user = createUser(userId, "TRUSTED");
    when(userServiceClient.getUser(userId)).thenReturn(Optional.of(user));
    when(moderationItemRepository.countRejectionsSince(eq(userId), any(Instant.class)))
        .thenReturn(4);
    when(abuseReportRepository.countActiveReportsAgainst(userId)).thenReturn(5);

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isTrue();
    verify(userServiceClient).updateTrustTier(userId, "NEW", "AUTO_DEMOTION");
  }

  @Test
  void checkAndDemote_userNotFound_returnsFalse() {
    // Arrange
    UUID userId = UUID.randomUUID();
    when(userServiceClient.getUser(userId)).thenReturn(Optional.empty());

    // Act
    boolean result = trustDemotionService.checkAndDemote(userId);

    // Assert
    assertThat(result).isFalse();
    verify(userServiceClient, never()).updateTrustTier(any(), any(), any());
  }

  private UserSummary createUser(UUID userId, String trustTier) {
    Instant createdAt = Instant.now().minus(60, ChronoUnit.DAYS);
    UserStats stats = new UserStats(20, 15);
    return new UserSummary(userId, "testuser", null, trustTier, stats, createdAt);
  }
}
