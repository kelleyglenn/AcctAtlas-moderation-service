package com.accountabilityatlas.moderationservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AbuseReportTest {

  @Test
  void onCreate_setsCreatedAtWhenNull() {
    // Arrange
    AbuseReport report = new AbuseReport();
    assertThat(report.getCreatedAt()).isNull();

    // Act
    report.onCreate();

    // Assert
    assertThat(report.getCreatedAt()).isNotNull();
    assertThat(report.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    // Arrange
    AbuseReport report = new AbuseReport();
    Instant existingTime = Instant.parse("2024-01-01T00:00:00Z");
    report.setCreatedAt(existingTime);

    // Act
    report.onCreate();

    // Assert
    assertThat(report.getCreatedAt()).isEqualTo(existingTime);
  }

  @Test
  void onCreate_setsStatusToOpenWhenNull() {
    // Arrange
    AbuseReport report = new AbuseReport();
    assertThat(report.getStatus()).isNull();

    // Act
    report.onCreate();

    // Assert
    assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
  }

  @Test
  void onCreate_preservesExistingStatus() {
    // Arrange
    AbuseReport report = new AbuseReport();
    report.setStatus(ReportStatus.RESOLVED);

    // Act
    report.onCreate();

    // Assert
    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
  }
}
