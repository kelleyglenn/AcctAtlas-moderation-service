package com.accountabilityatlas.moderationservice.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Client for calling video-service internal APIs. */
@Component
@Slf4j
public class VideoServiceClient {

  private final WebClient webClient;

  public VideoServiceClient(WebClient videoServiceWebClient) {
    this.webClient = videoServiceWebClient;
  }

  /**
   * Updates the status of a video.
   *
   * @param videoId the video ID
   * @param status the new status (e.g., "APPROVED", "REJECTED", "PENDING_REVIEW")
   * @throws VideoServiceException if the request fails
   */
  public void updateVideoStatus(UUID videoId, String status) {
    log.info("Updating video {} status to {}", videoId, status);
    try {
      webClient
          .put()
          .uri("/internal/videos/{id}/status", videoId)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new StatusUpdateRequest(status))
          .retrieve()
          .toBodilessEntity()
          .block();
      log.info("Successfully updated video {} status to {}", videoId, status);
    } catch (WebClientResponseException e) {
      log.error(
          "Failed to update video {} status: {} {}", videoId, e.getStatusCode(), e.getMessage());
      throw new VideoServiceException(
          "Failed to update video status: " + e.getStatusCode(), e.getStatusCode(), e);
    } catch (Exception e) {
      log.error("Failed to update video {} status: {}", videoId, e.getMessage());
      throw new VideoServiceException("Failed to update video status: " + e.getMessage(), e);
    }
  }

  /**
   * Adds a location to a video.
   *
   * @param videoId the video ID
   * @param locationId the location ID to add
   * @param isPrimary whether this is the primary location
   * @throws VideoServiceException if the request fails
   */
  public void addLocation(UUID videoId, UUID locationId, boolean isPrimary) {
    log.info("Adding location {} to video {} (primary={})", locationId, videoId, isPrimary);
    try {
      webClient
          .post()
          .uri("/internal/videos/{id}/locations", videoId)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(new AddLocationRequest(locationId, isPrimary))
          .retrieve()
          .toBodilessEntity()
          .block();
      log.info("Successfully added location {} to video {}", locationId, videoId);
    } catch (WebClientResponseException e) {
      log.error(
          "Failed to add location {} to video {}: {} {}",
          locationId,
          videoId,
          e.getStatusCode(),
          e.getMessage());
      throw new VideoServiceException(
          "Failed to add location to video: " + e.getStatusCode(), e.getStatusCode(), e);
    } catch (Exception e) {
      log.error("Failed to add location {} to video {}: {}", locationId, videoId, e.getMessage());
      throw new VideoServiceException("Failed to add location to video: " + e.getMessage(), e);
    }
  }

  /**
   * Removes a location from a video.
   *
   * @param videoId the video ID
   * @param locationId the location ID to remove
   * @throws VideoServiceException if the request fails
   */
  public void removeLocation(UUID videoId, UUID locationId) {
    log.info("Removing location {} from video {}", locationId, videoId);
    try {
      webClient
          .delete()
          .uri("/internal/videos/{id}/locations/{locId}", videoId, locationId)
          .retrieve()
          .toBodilessEntity()
          .block();
      log.info("Successfully removed location {} from video {}", locationId, videoId);
    } catch (WebClientResponseException e) {
      // 404 is acceptable - the location may have already been removed
      if (e.getStatusCode().value() == 404) {
        log.info(
            "Location {} not found on video {} (may have already been removed)",
            locationId,
            videoId);
        return;
      }
      log.error(
          "Failed to remove location {} from video {}: {} {}",
          locationId,
          videoId,
          e.getStatusCode(),
          e.getMessage());
      throw new VideoServiceException(
          "Failed to remove location from video: " + e.getStatusCode(), e.getStatusCode(), e);
    } catch (Exception e) {
      log.error(
          "Failed to remove location {} from video {}: {}", locationId, videoId, e.getMessage());
      throw new VideoServiceException("Failed to remove location from video: " + e.getMessage(), e);
    }
  }

  /** Request body for status update. */
  public record StatusUpdateRequest(String status) {}

  /** Request body for adding a location. */
  public record AddLocationRequest(UUID locationId, boolean isPrimary) {}

  /** Exception thrown when video-service calls fail. */
  public static class VideoServiceException extends RuntimeException {
    private final HttpStatusCode httpStatusCode;

    public VideoServiceException(String message, Throwable cause) {
      super(message, cause);
      this.httpStatusCode = null;
    }

    public VideoServiceException(String message, HttpStatusCode httpStatusCode, Throwable cause) {
      super(message, cause);
      this.httpStatusCode = httpStatusCode;
    }

    public HttpStatusCode getHttpStatusCode() {
      return httpStatusCode;
    }
  }
}
