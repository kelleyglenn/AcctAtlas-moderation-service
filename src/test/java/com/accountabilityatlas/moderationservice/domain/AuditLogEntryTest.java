package com.accountabilityatlas.moderationservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditLogEntryTest {

  @Test
  void onCreate_setsCreatedAtWhenNull() {
    // Arrange
    AuditLogEntry entry = new AuditLogEntry();
    assertThat(entry.getCreatedAt()).isNull();

    // Act
    entry.onCreate();

    // Assert
    assertThat(entry.getCreatedAt()).isNotNull();
    assertThat(entry.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    // Arrange
    AuditLogEntry entry = new AuditLogEntry();
    Instant existingTime = Instant.parse("2024-01-01T00:00:00Z");
    entry.setCreatedAt(existingTime);

    // Act
    entry.onCreate();

    // Assert
    assertThat(entry.getCreatedAt()).isEqualTo(existingTime);
  }
}
