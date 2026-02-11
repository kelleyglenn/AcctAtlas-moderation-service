package com.accountabilityatlas.moderationservice.client;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Client for calling user-service APIs. */
@Component
@Slf4j
public class UserServiceClient {

  private final WebClient webClient;

  public UserServiceClient(WebClient userServiceWebClient) {
    this.webClient = userServiceWebClient;
  }

  /**
   * Gets a user's public profile including stats.
   *
   * @param userId the user ID
   * @return the user summary if found, empty if not found
   * @throws UserServiceException if the request fails (other than 404)
   */
  public Optional<UserSummary> getUser(UUID userId) {
    log.debug("Fetching user {}", userId);
    try {
      UserSummary user =
          webClient
              .get()
              .uri("/users/{id}", userId)
              .retrieve()
              .bodyToMono(UserSummary.class)
              .block();
      log.debug("Successfully fetched user {}", userId);
      return Optional.ofNullable(user);
    } catch (WebClientResponseException.NotFound e) {
      log.debug("User {} not found", userId);
      return Optional.empty();
    } catch (WebClientResponseException e) {
      log.error("Failed to fetch user {}: {} {}", userId, e.getStatusCode(), e.getMessage());
      throw new UserServiceException(
          "Failed to fetch user: " + e.getStatusCode(), e.getStatusCode(), e);
    } catch (Exception e) {
      log.error("Failed to fetch user {}: {}", userId, e.getMessage());
      throw new UserServiceException("Failed to fetch user: " + e.getMessage(), e);
    }
  }

  /**
   * Updates a user's trust tier.
   *
   * @param userId the user ID
   * @param newTier the new trust tier (NEW, TRUSTED, MODERATOR, ADMIN)
   * @param reason optional reason for the change (for audit logging)
   * @throws UserServiceException if the request fails
   */
  public void updateTrustTier(UUID userId, String newTier, String reason) {
    log.info("Updating user {} trust tier to {}", userId, newTier);
    try {
      webClient
          .put()
          .uri("/users/{id}/trust-tier", userId)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new UpdateTrustTierRequest(newTier, reason))
          .retrieve()
          .toBodilessEntity()
          .block();
      log.info("Successfully updated user {} trust tier to {}", userId, newTier);
    } catch (WebClientResponseException e) {
      log.error(
          "Failed to update user {} trust tier: {} {}", userId, e.getStatusCode(), e.getMessage());
      throw new UserServiceException(
          "Failed to update trust tier: " + e.getStatusCode(), e.getStatusCode(), e);
    } catch (Exception e) {
      log.error("Failed to update user {} trust tier: {}", userId, e.getMessage());
      throw new UserServiceException("Failed to update trust tier: " + e.getMessage(), e);
    }
  }

  /** User summary containing profile and stats. */
  public record UserSummary(
      UUID id,
      String displayName,
      String avatarUrl,
      String trustTier,
      UserStats stats,
      Instant createdAt) {}

  /** User contribution statistics. */
  public record UserStats(int submissionCount, int approvedCount) {}

  /** Request body for updating trust tier. */
  public record UpdateTrustTierRequest(String trustTier, String reason) {}

  /** Exception thrown when user-service calls fail. */
  @Getter
  public static class UserServiceException extends RuntimeException {
    private final HttpStatusCode httpStatusCode;

    public UserServiceException(String message, Throwable cause) {
      super(message, cause);
      this.httpStatusCode = null;
    }

    public UserServiceException(String message, HttpStatusCode httpStatusCode, Throwable cause) {
      super(message, cause);
      this.httpStatusCode = httpStatusCode;
    }
  }
}
