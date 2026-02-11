package com.accountabilityatlas.moderationservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.exception.ModerationItemNotFoundException;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModerationQueueController.class)
class ModerationQueueControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ModerationService moderationService;

  @MockitoBean private VideoServiceClient videoServiceClient;

  // ============================================
  // updateVideoMetadata tests
  // ============================================

  @Test
  void updateVideoMetadata_asModerator_pendingItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    when(moderationService.getItem(itemId)).thenReturn(item);

    String requestBody =
        """
        {
          "amendments": ["FIRST", "FOURTH"],
          "participants": ["POLICE", "CITIZEN"],
          "videoDate": "2025-01-15"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            put("/moderation/queue/{id}/video", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()))
        .andExpect(jsonPath("$.contentId").value(videoId.toString()));

    verify(videoServiceClient).updateVideoMetadata(eq(videoId), any());
  }

  @Test
  void updateVideoMetadata_asModerator_approvedItem_forbidden() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Use videoDate only to avoid minItems validation on amendments/participants
    String requestBody =
        """
        {
          "videoDate": "2025-01-15"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            put("/moderation/queue/{id}/video", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("STATUS_NOT_ALLOWED"));
  }

  @Test
  void updateVideoMetadata_asAdmin_approvedItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Use videoDate only to avoid minItems validation on amendments/participants
    String requestBody =
        """
        {
          "videoDate": "2025-01-15"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            put("/moderation/queue/{id}/video", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()));

    verify(videoServiceClient).updateVideoMetadata(eq(videoId), any());
  }

  @Test
  void updateVideoMetadata_itemNotFound_returns404() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    when(moderationService.getItem(itemId)).thenThrow(new ModerationItemNotFoundException(itemId));

    // Use videoDate only to avoid minItems validation on amendments/participants
    String requestBody =
        """
        {
          "videoDate": "2025-01-15"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            put("/moderation/queue/{id}/video", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateVideoMetadata_videoServiceError_returns500() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    when(moderationService.getItem(itemId)).thenReturn(item);
    doThrow(new VideoServiceException("Failed to update video", null))
        .when(videoServiceClient)
        .updateVideoMetadata(eq(videoId), any());

    // Use videoDate only to avoid minItems validation on amendments/participants
    String requestBody =
        """
        {
          "videoDate": "2025-01-15"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            put("/moderation/queue/{id}/video", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isInternalServerError());
  }

  // ============================================
  // addVideoLocation tests
  // ============================================

  @Test
  void addVideoLocation_asModerator_pendingItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    when(moderationService.getItem(itemId)).thenReturn(item);

    String requestBody =
        String.format(
            """
        {
          "locationId": "%s",
          "isPrimary": true
        }
        """,
            locationId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/locations", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()));

    verify(videoServiceClient).addLocation(videoId, locationId, true);
  }

  @Test
  void addVideoLocation_asModerator_approvedItem_forbidden() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    String requestBody =
        String.format(
            """
        {
          "locationId": "%s",
          "isPrimary": false
        }
        """,
            locationId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/locations", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("STATUS_NOT_ALLOWED"));
  }

  @Test
  void addVideoLocation_asAdmin_rejectedItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.REJECTED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    String requestBody =
        String.format(
            """
        {
          "locationId": "%s",
          "isPrimary": false
        }
        """,
            locationId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/locations", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()));

    verify(videoServiceClient).addLocation(videoId, locationId, false);
  }

  @Test
  void addVideoLocation_itemNotFound_returns404() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    when(moderationService.getItem(itemId)).thenThrow(new ModerationItemNotFoundException(itemId));

    String requestBody =
        String.format(
            """
        {
          "locationId": "%s"
        }
        """,
            locationId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/locations", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound());
  }

  // ============================================
  // removeVideoLocation tests
  // ============================================

  @Test
  void removeVideoLocation_asModerator_pendingItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Act & Assert
    mockMvc
        .perform(
            delete("/moderation/queue/{id}/locations/{locationId}", itemId, locationId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isNoContent());

    verify(videoServiceClient).removeLocation(videoId, locationId);
  }

  @Test
  void removeVideoLocation_asModerator_approvedItem_forbidden() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Act & Assert
    mockMvc
        .perform(
            delete("/moderation/queue/{id}/locations/{locationId}", itemId, locationId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("STATUS_NOT_ALLOWED"));
  }

  @Test
  void removeVideoLocation_asAdmin_approvedItem_success() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Act & Assert
    mockMvc
        .perform(
            delete("/moderation/queue/{id}/locations/{locationId}", itemId, locationId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isNoContent());

    verify(videoServiceClient).removeLocation(videoId, locationId);
  }

  @Test
  void removeVideoLocation_itemNotFound_returns404() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    when(moderationService.getItem(itemId)).thenThrow(new ModerationItemNotFoundException(itemId));

    // Act & Assert
    mockMvc
        .perform(
            delete("/moderation/queue/{id}/locations/{locationId}", itemId, locationId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isNotFound());
  }

  // ============================================
  // Helper methods
  // ============================================

  private ModerationItem createModerationItem(UUID itemId, UUID videoId, ModerationStatus status) {
    ModerationItem item = new ModerationItem();
    item.setId(itemId);
    item.setContentType(ContentType.VIDEO);
    item.setContentId(videoId);
    item.setSubmitterId(UUID.randomUUID());
    item.setStatus(status);
    item.setPriority(0);
    item.setCreatedAt(Instant.now());
    return item;
  }
}
