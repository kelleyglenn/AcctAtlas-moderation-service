package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.client.UserServiceClient;
import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserSummary;
import com.accountabilityatlas.moderationservice.repository.AbuseReportRepository;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for handling automatic trust tier demotion from TRUSTED to NEW.
 *
 * <p>Demotion is triggered when EITHER condition is met:
 *
 * <ul>
 *   <li>Rejections in last 30 days >= 3
 *   <li>Active abuse reports against their content >= 3
 * </ul>
 *
 * <p>Only users with TRUSTED tier can be automatically demoted. MODERATOR and ADMIN users require
 * manual demotion by an admin.
 */
@Service
@Slf4j
public class TrustDemotionService {

  private static final int DEMOTION_REJECTION_THRESHOLD = 3;
  private static final int DEMOTION_REPORT_THRESHOLD = 3;
  private static final int REJECTION_LOOKBACK_DAYS = 30;
  private static final String TRUSTED_TIER = "TRUSTED";
  private static final String NEW_TIER = "NEW";
  private static final String AUTO_DEMOTION_REASON = "AUTO_DEMOTION";

  private final UserServiceClient userServiceClient;
  private final ModerationItemRepository moderationItemRepository;
  private final AbuseReportRepository abuseReportRepository;

  public TrustDemotionService(
      UserServiceClient userServiceClient,
      ModerationItemRepository moderationItemRepository,
      AbuseReportRepository abuseReportRepository) {
    this.userServiceClient = userServiceClient;
    this.moderationItemRepository = moderationItemRepository;
    this.abuseReportRepository = abuseReportRepository;
  }

  /**
   * Checks if a TRUSTED user should be demoted to NEW tier.
   *
   * <p>This method should be called after each VideoRejected event to check if the user has
   * exceeded the demotion thresholds.
   *
   * @param userId the user ID to check
   * @return true if the user was demoted, false otherwise
   */
  public boolean checkAndDemote(UUID userId) {
    log.debug("Checking trust demotion eligibility for user {}", userId);

    Optional<UserSummary> userOpt = userServiceClient.getUser(userId);
    if (userOpt.isEmpty()) {
      log.debug("User {} not found, skipping demotion check", userId);
      return false;
    }

    UserSummary user = userOpt.get();

    // Only TRUSTED users can be automatically demoted
    if (!TRUSTED_TIER.equals(user.trustTier())) {
      log.debug(
          "User {} has trust tier {}, not eligible for automatic demotion",
          userId,
          user.trustTier());
      return false;
    }

    // Check for recent rejections
    Instant rejectionLookbackStart = Instant.now().minus(REJECTION_LOOKBACK_DAYS, ChronoUnit.DAYS);
    int recentRejections =
        moderationItemRepository.countRejectionsSince(userId, rejectionLookbackStart);

    // Check for active abuse reports
    int activeReports = abuseReportRepository.countActiveReportsAgainst(userId);

    // Demotion is triggered if EITHER threshold is met
    boolean shouldDemote =
        recentRejections >= DEMOTION_REJECTION_THRESHOLD
            || activeReports >= DEMOTION_REPORT_THRESHOLD;

    if (!shouldDemote) {
      log.debug(
          "User {} has {} rejections and {} active reports, below demotion thresholds",
          userId,
          recentRejections,
          activeReports);
      return false;
    }

    // Determine the demotion reason for logging
    String demotionReason;
    if (recentRejections >= DEMOTION_REJECTION_THRESHOLD
        && activeReports >= DEMOTION_REPORT_THRESHOLD) {
      demotionReason =
          String.format(
              "both %d rejections in last %d days and %d active abuse reports",
              recentRejections, REJECTION_LOOKBACK_DAYS, activeReports);
    } else if (recentRejections >= DEMOTION_REJECTION_THRESHOLD) {
      demotionReason =
          String.format(
              "%d rejections in last %d days (threshold: %d)",
              recentRejections, REJECTION_LOOKBACK_DAYS, DEMOTION_REJECTION_THRESHOLD);
    } else {
      demotionReason =
          String.format(
              "%d active abuse reports (threshold: %d)", activeReports, DEMOTION_REPORT_THRESHOLD);
    }

    log.info(
        "User {} meets demotion criteria due to {}, demoting from TRUSTED to NEW",
        userId,
        demotionReason);
    userServiceClient.updateTrustTier(userId, NEW_TIER, AUTO_DEMOTION_REASON);
    return true;
  }
}
