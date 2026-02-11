package com.accountabilityatlas.moderationservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient;
import com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import com.accountabilityatlas.moderationservice.exception.ItemAlreadyReviewedException;
import com.accountabilityatlas.moderationservice.exception.ModerationItemNotFoundException;
import com.accountabilityatlas.moderationservice.service.ModerationService;
import com.accountabilityatlas.moderationservice.service.ModerationService.QueueStats;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
  // listModerationQueue tests
  // ============================================

  @Test
  void listModerationQueue_defaultParams_returnsPendingItems() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    Page<ModerationItem> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
    when(moderationService.getQueue(eq(ModerationStatus.PENDING), eq(null), any()))
        .thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "createdAt")
                .param("direction", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").value(itemId.toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0));
  }

  @Test
  void listModerationQueue_withStatusAndContentType_appliesFilters() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    Page<ModerationItem> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
    when(moderationService.getQueue(eq(ModerationStatus.APPROVED), eq(ContentType.VIDEO), any()))
        .thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .param("status", "APPROVED")
                .param("contentType", "VIDEO")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "createdAt")
                .param("direction", "desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("APPROVED"));
  }

  // ============================================
  // getModerationItem tests
  // ============================================

  @Test
  void getModerationItem_existingId_returnsItem() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.PENDING);
    when(moderationService.getItem(itemId)).thenReturn(item);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue/{id}", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()))
        .andExpect(jsonPath("$.contentType").value("VIDEO"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void getModerationItem_notFound_returns404() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    when(moderationService.getItem(itemId)).thenThrow(new ModerationItemNotFoundException(itemId));

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue/{id}", itemId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isNotFound());
  }

  // ============================================
  // approveContent tests
  // ============================================

  @Test
  void approveContent_validRequest_returnsApprovedItem() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.APPROVED);
    item.setReviewerId(reviewerId);
    item.setReviewedAt(Instant.now());
    when(moderationService.approve(itemId, reviewerId)).thenReturn(item);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/approve", itemId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(reviewerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reviewerId").value(reviewerId.toString()));
  }

  @Test
  void approveContent_alreadyReviewed_returns409() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    when(moderationService.approve(itemId, reviewerId))
        .thenThrow(new ItemAlreadyReviewedException(itemId));

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/approve", itemId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(reviewerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
  }

  // ============================================
  // rejectContent tests
  // ============================================

  @Test
  void rejectContent_validRequest_returnsRejectedItem() throws Exception {
    // Arrange
    UUID itemId = UUID.randomUUID();
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    ModerationItem item = createModerationItem(itemId, videoId, ModerationStatus.REJECTED);
    item.setReviewerId(reviewerId);
    item.setReviewedAt(Instant.now());
    item.setRejectionReason("Violates community guidelines");
    when(moderationService.reject(itemId, reviewerId, "Violates community guidelines"))
        .thenReturn(item);

    String requestBody =
        """
        {
          "reason": "Violates community guidelines"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/queue/{id}/reject", itemId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(reviewerId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType("application/json")
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId.toString()))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Violates community guidelines"));
  }

  // ============================================
  // getQueueStats tests
  // ============================================

  @Test
  void getQueueStats_returnsStats() throws Exception {
    // Arrange
    QueueStats stats = new QueueStats(10, 5, 2, 15.5);
    when(moderationService.getQueueStats()).thenReturn(stats);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue/stats")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending").value(10))
        .andExpect(jsonPath("$.approvedToday").value(5))
        .andExpect(jsonPath("$.rejectedToday").value(2))
        .andExpect(jsonPath("$.avgReviewTimeMinutes").value(15.5));
  }

  @Test
  void getQueueStats_nullAvgTime_returnsNullAvgTime() throws Exception {
    // Arrange
    QueueStats stats = new QueueStats(0, 0, 0, null);
    when(moderationService.getQueueStats()).thenReturn(stats);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/queue/stats")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending").value(0))
        .andExpect(jsonPath("$.avgReviewTimeMinutes").isEmpty());
  }

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
