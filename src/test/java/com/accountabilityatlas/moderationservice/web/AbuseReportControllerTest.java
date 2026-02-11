package com.accountabilityatlas.moderationservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountabilityatlas.moderationservice.domain.AbuseReason;
import com.accountabilityatlas.moderationservice.domain.AbuseReport;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ReportStatus;
import com.accountabilityatlas.moderationservice.exception.AbuseReportNotFoundException;
import com.accountabilityatlas.moderationservice.service.AbuseReportService;
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

@WebMvcTest(AbuseReportController.class)
class AbuseReportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AbuseReportService abuseReportService;

  // ============================================
  // submitAbuseReport tests
  // ============================================

  @Test
  void submitAbuseReport_validRequest_returnsCreated() throws Exception {
    // Arrange
    UUID reporterId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(UUID.randomUUID(), contentId, reporterId);
    when(abuseReportService.submitReport(
            eq(ContentType.VIDEO), eq(contentId), eq(reporterId), eq(AbuseReason.SPAM), any()))
        .thenReturn(report);

    String requestBody =
        String.format(
            """
            {
              "contentType": "VIDEO",
              "contentId": "%s",
              "reason": "SPAM",
              "description": "This is spam content"
            }
            """,
            contentId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/reports")
                .with(jwt().jwt(jwt -> jwt.subject(reporterId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(report.getId().toString()))
        .andExpect(jsonPath("$.contentType").value("VIDEO"))
        .andExpect(jsonPath("$.reason").value("SPAM"));
  }

  @Test
  void submitAbuseReport_locationContent_success() throws Exception {
    // Arrange
    UUID reporterId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(UUID.randomUUID(), contentId, reporterId);
    report.setContentType(ContentType.LOCATION);
    report.setReason(AbuseReason.MISINFORMATION);
    when(abuseReportService.submitReport(any(), any(), any(), any(), any())).thenReturn(report);

    String requestBody =
        String.format(
            """
            {
              "contentType": "LOCATION",
              "contentId": "%s",
              "reason": "MISINFORMATION"
            }
            """,
            contentId);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/reports")
                .with(jwt().jwt(jwt -> jwt.subject(reporterId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("LOCATION"))
        .andExpect(jsonPath("$.reason").value("MISINFORMATION"));
  }

  // ============================================
  // listAbuseReports tests
  // ============================================

  @Test
  void listAbuseReports_defaultStatus_returnsOpenReports() throws Exception {
    // Arrange
    AbuseReport report = createAbuseReport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    Page<AbuseReport> page = new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1);
    when(abuseReportService.listReports(eq(ReportStatus.OPEN), any())).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/reports")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].id").value(report.getId().toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));

    verify(abuseReportService).listReports(eq(ReportStatus.OPEN), any());
  }

  @Test
  void listAbuseReports_resolvedStatus_returnsResolvedReports() throws Exception {
    // Arrange
    AbuseReport report = createAbuseReport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    report.setStatus(ReportStatus.RESOLVED);
    Page<AbuseReport> page = new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1);
    when(abuseReportService.listReports(eq(ReportStatus.RESOLVED), any())).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/reports")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .param("status", "RESOLVED")
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("RESOLVED"));

    verify(abuseReportService).listReports(eq(ReportStatus.RESOLVED), any());
  }

  @Test
  void listAbuseReports_emptyList_returnsEmptyPage() throws Exception {
    // Arrange
    Page<AbuseReport> page = Page.empty(PageRequest.of(0, 20));
    when(abuseReportService.listReports(any(), any())).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/reports")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ============================================
  // getAbuseReport tests
  // ============================================

  @Test
  void getAbuseReport_existingId_returnsReport() throws Exception {
    // Arrange
    UUID reportId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(reportId, UUID.randomUUID(), UUID.randomUUID());
    when(abuseReportService.getReport(reportId)).thenReturn(report);

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/reports/{id}", reportId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reportId.toString()))
        .andExpect(jsonPath("$.contentType").value("VIDEO"))
        .andExpect(jsonPath("$.reason").value("SPAM"));
  }

  @Test
  void getAbuseReport_notFound_returns404() throws Exception {
    // Arrange
    UUID reportId = UUID.randomUUID();
    when(abuseReportService.getReport(reportId))
        .thenThrow(new AbuseReportNotFoundException(reportId));

    // Act & Assert
    mockMvc
        .perform(
            get("/moderation/reports/{id}", reportId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
        .andExpect(status().isNotFound());
  }

  // ============================================
  // resolveAbuseReport tests
  // ============================================

  @Test
  void resolveAbuseReport_validRequest_returnsResolvedReport() throws Exception {
    // Arrange
    UUID reportId = UUID.randomUUID();
    UUID moderatorId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(reportId, UUID.randomUUID(), UUID.randomUUID());
    report.setStatus(ReportStatus.RESOLVED);
    report.setResolvedBy(moderatorId);
    report.setResolution("Content removed");
    when(abuseReportService.resolve(reportId, moderatorId, "Content removed")).thenReturn(report);

    String requestBody =
        """
        {
          "resolution": "Content removed"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/reports/{id}/resolve", reportId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(moderatorId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reportId.toString()))
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.resolution").value("Content removed"));
  }

  // ============================================
  // dismissAbuseReport tests
  // ============================================

  @Test
  void dismissAbuseReport_withReason_returnsDismissedReport() throws Exception {
    // Arrange
    UUID reportId = UUID.randomUUID();
    UUID moderatorId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(reportId, UUID.randomUUID(), UUID.randomUUID());
    report.setStatus(ReportStatus.DISMISSED);
    report.setResolvedBy(moderatorId);
    report.setResolution("Not a violation");
    when(abuseReportService.dismiss(reportId, moderatorId, "Not a violation")).thenReturn(report);

    String requestBody =
        """
        {
          "reason": "Not a violation"
        }
        """;

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/reports/{id}/dismiss", reportId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(moderatorId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reportId.toString()))
        .andExpect(jsonPath("$.status").value("DISMISSED"));
  }

  @Test
  void dismissAbuseReport_withoutReason_success() throws Exception {
    // Arrange
    UUID reportId = UUID.randomUUID();
    UUID moderatorId = UUID.randomUUID();
    AbuseReport report = createAbuseReport(reportId, UUID.randomUUID(), UUID.randomUUID());
    report.setStatus(ReportStatus.DISMISSED);
    report.setResolvedBy(moderatorId);
    when(abuseReportService.dismiss(reportId, moderatorId, null)).thenReturn(report);

    // Act & Assert
    mockMvc
        .perform(
            post("/moderation/reports/{id}/dismiss", reportId)
                .with(
                    jwt()
                        .jwt(jwt -> jwt.subject(moderatorId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MODERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
  }

  // ============================================
  // Helper methods
  // ============================================

  private AbuseReport createAbuseReport(UUID id, UUID contentId, UUID reporterId) {
    AbuseReport report = new AbuseReport();
    report.setId(id);
    report.setContentType(ContentType.VIDEO);
    report.setContentId(contentId);
    report.setReporterId(reporterId);
    report.setReason(AbuseReason.SPAM);
    report.setDescription("Test description");
    report.setStatus(ReportStatus.OPEN);
    report.setCreatedAt(Instant.now());
    return report;
  }
}
