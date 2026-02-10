package com.accountabilityatlas.moderationservice.web;

import com.accountabilityatlas.moderationservice.domain.AbuseReason;
import com.accountabilityatlas.moderationservice.domain.AbuseReport;
import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ReportStatus;
import com.accountabilityatlas.moderationservice.service.AbuseReportService;
import com.accountabilityatlas.moderationservice.web.api.ReportsApi;
import com.accountabilityatlas.moderationservice.web.model.AbuseReportDetail;
import com.accountabilityatlas.moderationservice.web.model.AbuseReportListResponse;
import com.accountabilityatlas.moderationservice.web.model.CreateAbuseReportRequest;
import com.accountabilityatlas.moderationservice.web.model.DismissReportRequest;
import com.accountabilityatlas.moderationservice.web.model.ResolveReportRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AbuseReportController implements ReportsApi {

  private final AbuseReportService abuseReportService;

  @Override
  public ResponseEntity<com.accountabilityatlas.moderationservice.web.model.AbuseReport>
      submitAbuseReport(CreateAbuseReportRequest createAbuseReportRequest) {
    UUID reporterId = getCurrentUserId();

    AbuseReport report =
        abuseReportService.submitReport(
            toDomainContentType(createAbuseReportRequest.getContentType()),
            createAbuseReportRequest.getContentId(),
            reporterId,
            toDomainAbuseReason(createAbuseReportRequest.getReason()),
            createAbuseReportRequest.getDescription());

    return ResponseEntity.status(HttpStatus.CREATED).body(toApiAbuseReport(report));
  }

  @Override
  public ResponseEntity<AbuseReportListResponse> listAbuseReports(
      @Nullable com.accountabilityatlas.moderationservice.web.model.ReportStatus status,
      @Nullable com.accountabilityatlas.moderationservice.web.model.ContentType contentType,
      Integer page,
      Integer size) {

    ReportStatus domainStatus = status != null ? toDomainReportStatus(status) : ReportStatus.OPEN;
    Pageable pageable = PageRequest.of(page, size);

    // Note: contentType filtering not currently supported by service layer
    Page<AbuseReport> reportPage = abuseReportService.listReports(domainStatus, pageable);

    List<com.accountabilityatlas.moderationservice.web.model.AbuseReport> reports =
        reportPage.getContent().stream().map(this::toApiAbuseReport).toList();

    AbuseReportListResponse response =
        new AbuseReportListResponse()
            .content(reports)
            .page(reportPage.getNumber())
            .size(reportPage.getSize())
            .totalElements((int) reportPage.getTotalElements())
            .totalPages(reportPage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<AbuseReportDetail> getAbuseReport(UUID id) {
    AbuseReport report = abuseReportService.getReport(id);
    return ResponseEntity.ok(toApiAbuseReportDetail(report));
  }

  @Override
  public ResponseEntity<AbuseReportDetail> resolveAbuseReport(
      UUID id, ResolveReportRequest resolveReportRequest) {
    UUID moderatorId = getCurrentUserId();
    AbuseReport report =
        abuseReportService.resolve(id, moderatorId, resolveReportRequest.getResolution());
    return ResponseEntity.ok(toApiAbuseReportDetail(report));
  }

  @Override
  public ResponseEntity<AbuseReportDetail> dismissAbuseReport(
      UUID id, @Nullable DismissReportRequest dismissReportRequest) {
    UUID moderatorId = getCurrentUserId();
    String reason = dismissReportRequest != null ? dismissReportRequest.getReason() : null;
    AbuseReport report = abuseReportService.dismiss(id, moderatorId, reason);
    return ResponseEntity.ok(toApiAbuseReportDetail(report));
  }

  private UUID getCurrentUserId() {
    Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String subject = jwt.getSubject();
    return UUID.fromString(subject);
  }

  private ContentType toDomainContentType(
      com.accountabilityatlas.moderationservice.web.model.ContentType apiContentType) {
    return switch (apiContentType) {
      case VIDEO -> ContentType.VIDEO;
      case LOCATION -> ContentType.LOCATION;
    };
  }

  private AbuseReason toDomainAbuseReason(
      com.accountabilityatlas.moderationservice.web.model.AbuseReason apiReason) {
    return switch (apiReason) {
      case SPAM -> AbuseReason.SPAM;
      case INAPPROPRIATE -> AbuseReason.INAPPROPRIATE;
      case COPYRIGHT -> AbuseReason.COPYRIGHT;
      case MISINFORMATION -> AbuseReason.MISINFORMATION;
      case OTHER -> AbuseReason.OTHER;
    };
  }

  private ReportStatus toDomainReportStatus(
      com.accountabilityatlas.moderationservice.web.model.ReportStatus apiStatus) {
    return switch (apiStatus) {
      case OPEN -> ReportStatus.OPEN;
      case RESOLVED -> ReportStatus.RESOLVED;
      case DISMISSED -> ReportStatus.DISMISSED;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.ContentType toApiContentType(
      ContentType domainContentType) {
    return switch (domainContentType) {
      case VIDEO -> com.accountabilityatlas.moderationservice.web.model.ContentType.VIDEO;
      case LOCATION -> com.accountabilityatlas.moderationservice.web.model.ContentType.LOCATION;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.AbuseReason toApiAbuseReason(
      AbuseReason domainReason) {
    return switch (domainReason) {
      case SPAM -> com.accountabilityatlas.moderationservice.web.model.AbuseReason.SPAM;
      case INAPPROPRIATE ->
          com.accountabilityatlas.moderationservice.web.model.AbuseReason.INAPPROPRIATE;
      case COPYRIGHT -> com.accountabilityatlas.moderationservice.web.model.AbuseReason.COPYRIGHT;
      case MISINFORMATION ->
          com.accountabilityatlas.moderationservice.web.model.AbuseReason.MISINFORMATION;
      case OTHER -> com.accountabilityatlas.moderationservice.web.model.AbuseReason.OTHER;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.ReportStatus toApiReportStatus(
      ReportStatus domainStatus) {
    return switch (domainStatus) {
      case OPEN -> com.accountabilityatlas.moderationservice.web.model.ReportStatus.OPEN;
      case RESOLVED -> com.accountabilityatlas.moderationservice.web.model.ReportStatus.RESOLVED;
      case DISMISSED -> com.accountabilityatlas.moderationservice.web.model.ReportStatus.DISMISSED;
    };
  }

  private com.accountabilityatlas.moderationservice.web.model.AbuseReport toApiAbuseReport(
      AbuseReport report) {
    return new com.accountabilityatlas.moderationservice.web.model.AbuseReport()
        .id(report.getId())
        .contentType(toApiContentType(report.getContentType()))
        .contentId(report.getContentId())
        .reporterId(report.getReporterId())
        .reason(toApiAbuseReason(report.getReason()))
        .description(report.getDescription())
        .status(toApiReportStatus(report.getStatus()))
        .resolvedBy(report.getResolvedBy())
        .resolution(report.getResolution())
        .createdAt(toOffsetDateTime(report.getCreatedAt()));
  }

  private AbuseReportDetail toApiAbuseReportDetail(AbuseReport report) {
    return new AbuseReportDetail()
        .id(report.getId())
        .contentType(toApiContentType(report.getContentType()))
        .contentId(report.getContentId())
        .reporterId(report.getReporterId())
        .reason(toApiAbuseReason(report.getReason()))
        .description(report.getDescription())
        .status(toApiReportStatus(report.getStatus()))
        .resolvedBy(report.getResolvedBy())
        .resolution(report.getResolution())
        .createdAt(toOffsetDateTime(report.getCreatedAt()));
    // Note: reporter, resolver, and contentPreview would be populated
    // by enriching with data from user-service and video-service
  }

  @Nullable
  private OffsetDateTime toOffsetDateTime(@Nullable java.time.Instant instant) {
    if (instant == null) {
      return null;
    }
    return instant.atOffset(ZoneOffset.UTC);
  }
}
