package com.accountabilityatlas.moderationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accountabilityatlas.moderationservice.domain.AbuseReason;
import com.accountabilityatlas.moderationservice.domain.AbuseReport;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ReportStatus;
import com.accountabilityatlas.moderationservice.exception.AbuseReportNotFoundException;
import com.accountabilityatlas.moderationservice.repository.AbuseReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AbuseReportServiceTest {

  @Mock private AbuseReportRepository abuseReportRepository;
  @Mock private AuditLogService auditLogService;

  private AbuseReportService abuseReportService;

  @BeforeEach
  void setUp() {
    abuseReportService = new AbuseReportService(abuseReportRepository, auditLogService);
  }

  @Test
  void submitReport_validInput_createsReportWithOpenStatus() {
    // Arrange
    UUID contentId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();
    String description = "This content violates community guidelines";
    when(abuseReportRepository.save(any(AbuseReport.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    AbuseReport result =
        abuseReportService.submitReport(
            ContentType.VIDEO, contentId, reporterId, AbuseReason.INAPPROPRIATE, description);

    // Assert
    assertThat(result.getContentType()).isEqualTo(ContentType.VIDEO);
    assertThat(result.getContentId()).isEqualTo(contentId);
    assertThat(result.getReporterId()).isEqualTo(reporterId);
    assertThat(result.getReason()).isEqualTo(AbuseReason.INAPPROPRIATE);
    assertThat(result.getDescription()).isEqualTo(description);
    assertThat(result.getStatus()).isEqualTo(ReportStatus.OPEN);
  }

  @Test
  void getReport_existingId_returnsReport() {
    // Arrange
    UUID id = UUID.randomUUID();
    AbuseReport report = new AbuseReport();
    report.setId(id);
    when(abuseReportRepository.findById(id)).thenReturn(Optional.of(report));

    // Act
    AbuseReport result = abuseReportService.getReport(id);

    // Assert
    assertThat(result.getId()).isEqualTo(id);
  }

  @Test
  void getReport_nonExistingId_throwsException() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(abuseReportRepository.findById(id)).thenReturn(Optional.empty());

    // Act
    Throwable thrown = catchThrowable(() -> abuseReportService.getReport(id));

    // Assert
    assertThat(thrown).isInstanceOf(AbuseReportNotFoundException.class);
  }

  @Test
  void resolve_openReport_setsResolvedStatus() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID resolverId = UUID.randomUUID();
    String resolutionNotes = "Content was reviewed and action taken";
    AbuseReport report = new AbuseReport();
    report.setId(id);
    report.setStatus(ReportStatus.OPEN);
    when(abuseReportRepository.findById(id)).thenReturn(Optional.of(report));
    when(abuseReportRepository.save(any(AbuseReport.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    AbuseReport result = abuseReportService.resolve(id, resolverId, resolutionNotes);

    // Assert
    assertThat(result.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(result.getResolvedBy()).isEqualTo(resolverId);
    assertThat(result.getResolution()).isEqualTo(resolutionNotes);
    verify(auditLogService).logAction(resolverId, "RESOLVE", "ABUSE_REPORT", id, resolutionNotes);
  }

  @Test
  void dismiss_openReport_setsDismissedStatus() {
    // Arrange
    UUID id = UUID.randomUUID();
    UUID resolverId = UUID.randomUUID();
    String dismissalReason = "Report was determined to be unfounded";
    AbuseReport report = new AbuseReport();
    report.setId(id);
    report.setStatus(ReportStatus.OPEN);
    when(abuseReportRepository.findById(id)).thenReturn(Optional.of(report));
    when(abuseReportRepository.save(any(AbuseReport.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    AbuseReport result = abuseReportService.dismiss(id, resolverId, dismissalReason);

    // Assert
    assertThat(result.getStatus()).isEqualTo(ReportStatus.DISMISSED);
    assertThat(result.getResolvedBy()).isEqualTo(resolverId);
    assertThat(result.getResolution()).isEqualTo(dismissalReason);
    verify(auditLogService).logAction(resolverId, "DISMISS", "ABUSE_REPORT", id, dismissalReason);
  }

  @Test
  void listReports_byStatus_returnsFilteredReports() {
    // Arrange
    AbuseReport report1 = new AbuseReport();
    report1.setId(UUID.randomUUID());
    report1.setStatus(ReportStatus.OPEN);
    AbuseReport report2 = new AbuseReport();
    report2.setId(UUID.randomUUID());
    report2.setStatus(ReportStatus.OPEN);
    Page<AbuseReport> page = new PageImpl<>(List.of(report1, report2));
    Pageable pageable = PageRequest.of(0, 20);
    when(abuseReportRepository.findByStatus(eq(ReportStatus.OPEN), any(Pageable.class)))
        .thenReturn(page);

    // Act
    Page<AbuseReport> result = abuseReportService.listReports(ReportStatus.OPEN, pageable);

    // Assert
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent()).allMatch(r -> r.getStatus() == ReportStatus.OPEN);
    verify(abuseReportRepository).findByStatus(ReportStatus.OPEN, pageable);
  }
}
