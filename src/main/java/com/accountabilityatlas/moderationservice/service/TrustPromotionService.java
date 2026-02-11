package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.client.UserServiceClient;
import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserSummary;
import com.accountabilityatlas.moderationservice.repository.AbuseReportRepository;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for handling automatic trust tier promotion from NEW to TRUSTED.
 *
 * <p>Promotion criteria (all must be met):
 *
 * <ul>
 *   <li>Account age >= 30 days
 *   <li>Approved submissions >= 10
 *   <li>Rejections in last 30 days = 0
 *   <li>Active abuse reports against their content = 0
 * </ul>
 */
@Service
@Slf4j
public class TrustPromotionService {

  private static final int MINIMUM_ACCOUNT_AGE_DAYS = 30;
  private static final int MINIMUM_APPROVED_SUBMISSIONS = 10;
  private static final int REJECTION_LOOKBACK_DAYS = 30;
  private static final String NEW_TIER = "NEW";
  private static final String TRUSTED_TIER = "TRUSTED";
  private static final String AUTO_PROMOTION_REASON = "AUTO_PROMOTION";

  private final UserServiceClient userServiceClient;
  private final ModerationItemRepository moderationItemRepository;
  private final AbuseReportRepository abuseReportRepository;

  public TrustPromotionService(
      UserServiceClient userServiceClient,
      ModerationItemRepository moderationItemRepository,
      AbuseReportRepository abuseReportRepository) {
    this.userServiceClient = userServiceClient;
    this.moderationItemRepository = moderationItemRepository;
    this.abuseReportRepository = abuseReportRepository;
  }

  /**
   * Checks if a user qualifies for automatic promotion from NEW to TRUSTED tier.
   *
   * <p>If the user meets all promotion criteria, their trust tier is updated via the user-service.
   *
   * @param userId the user ID to check
   * @return true if the user was promoted, false otherwise
   */
  public boolean checkAndPromote(UUID userId) {
    log.debug("Checking trust promotion eligibility for user {}", userId);

    Optional<UserSummary> userOpt = userServiceClient.getUser(userId);
    if (userOpt.isEmpty()) {
      log.debug("User {} not found, skipping promotion check", userId);
      return false;
    }

    UserSummary user = userOpt.get();

    // Only NEW users can be promoted to TRUSTED automatically
    if (!NEW_TIER.equals(user.trustTier())) {
      log.debug(
          "User {} has trust tier {}, not eligible for NEW->TRUSTED promotion",
          userId,
          user.trustTier());
      return false;
    }

    // Check account age
    long accountAgeDays = Duration.between(user.createdAt(), Instant.now()).toDays();
    if (accountAgeDays < MINIMUM_ACCOUNT_AGE_DAYS) {
      log.debug(
          "User {} account age {} days < {} required",
          userId,
          accountAgeDays,
          MINIMUM_ACCOUNT_AGE_DAYS);
      return false;
    }

    // Check approved submissions count
    int approvedCount = user.stats() != null ? user.stats().approvedCount() : 0;
    if (approvedCount < MINIMUM_APPROVED_SUBMISSIONS) {
      log.debug(
          "User {} has {} approved submissions < {} required",
          userId,
          approvedCount,
          MINIMUM_APPROVED_SUBMISSIONS);
      return false;
    }

    // Check for recent rejections
    Instant rejectionLookbackStart = Instant.now().minus(REJECTION_LOOKBACK_DAYS, ChronoUnit.DAYS);
    int recentRejections =
        moderationItemRepository.countRejectionsSince(userId, rejectionLookbackStart);
    if (recentRejections > 0) {
      log.debug(
          "User {} has {} rejections in last {} days, not eligible for promotion",
          userId,
          recentRejections,
          REJECTION_LOOKBACK_DAYS);
      return false;
    }

    // Check for active abuse reports
    int activeReports = abuseReportRepository.countActiveReportsAgainst(userId);
    if (activeReports > 0) {
      log.debug(
          "User {} has {} active abuse reports, not eligible for promotion", userId, activeReports);
      return false;
    }

    // All criteria met - promote the user
    log.info("User {} meets all promotion criteria, promoting from NEW to TRUSTED", userId);
    userServiceClient.updateTrustTier(userId, TRUSTED_TIER, AUTO_PROMOTION_REASON);
    return true;
  }
}
