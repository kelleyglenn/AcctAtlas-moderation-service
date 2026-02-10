package com.accountabilityatlas.moderationservice.repository;

import com.accountabilityatlas.moderationservice.domain.AbuseReport;
import com.accountabilityatlas.moderationservice.domain.ReportStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AbuseReportRepository extends JpaRepository<AbuseReport, UUID> {

  Page<AbuseReport> findByStatus(ReportStatus status, Pageable pageable);

  @Query("SELECT COUNT(a) FROM AbuseReport a WHERE a.contentId IN "
      + "(SELECT m.contentId FROM ModerationItem m WHERE m.submitterId = :userId) "
      + "AND a.status = 'OPEN'")
  int countActiveReportsAgainst(UUID userId);

  long countByStatus(ReportStatus status);
}
