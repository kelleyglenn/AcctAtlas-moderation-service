package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.domain.AbuseReason;
import com.accountabilityatlas.moderationservice.domain.AbuseReport;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ReportStatus;
import com.accountabilityatlas.moderationservice.exception.AbuseReportNotFoundException;
import com.accountabilityatlas.moderationservice.repository.AbuseReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AbuseReportService {

  private final AbuseReportRepository abuseReportRepository;
  private final AuditLogService auditLogService;

  @Transactional
  public AbuseReport submitReport(
      ContentType contentType,
      UUID contentId,
      UUID reporterId,
      AbuseReason reason,
      String description) {
    AbuseReport report = new AbuseReport();
    report.setContentType(contentType);
    report.setContentId(contentId);
    report.setReporterId(reporterId);
    report.setReason(reason);
    report.setDescription(description);
    report.setStatus(ReportStatus.OPEN);
    return abuseReportRepository.save(report);
  }

  @Transactional(readOnly = true)
  public AbuseReport getReport(UUID id) {
    return getReportInternal(id);
  }

  @Transactional
  public AbuseReport resolve(UUID id, UUID moderatorId, String resolution) {
    AbuseReport report = getReportInternal(id);
    report.setStatus(ReportStatus.RESOLVED);
    report.setResolvedBy(moderatorId);
    report.setResolution(resolution);
    auditLogService.logAction(moderatorId, "RESOLVE", "ABUSE_REPORT", id, resolution);
    return abuseReportRepository.save(report);
  }

  @Transactional
  public AbuseReport dismiss(UUID id, UUID moderatorId, String reason) {
    AbuseReport report = getReportInternal(id);
    report.setStatus(ReportStatus.DISMISSED);
    report.setResolvedBy(moderatorId);
    report.setResolution(reason);
    auditLogService.logAction(moderatorId, "DISMISS", "ABUSE_REPORT", id, reason);
    return abuseReportRepository.save(report);
  }

  @Transactional(readOnly = true)
  public Page<AbuseReport> listReports(ReportStatus status, Pageable pageable) {
    return abuseReportRepository.findByStatus(status, pageable);
  }

  private AbuseReport getReportInternal(UUID id) {
    return abuseReportRepository
        .findById(id)
        .orElseThrow(() -> new AbuseReportNotFoundException(id));
  }
}
