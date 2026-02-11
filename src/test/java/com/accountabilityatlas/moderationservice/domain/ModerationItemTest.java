package com.accountabilityatlas.moderationservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModerationItemTest {

  @Test
  void onCreate_setsCreatedAtWhenNull() {
    // Arrange
    ModerationItem item = new ModerationItem();
    assertThat(item.getCreatedAt()).isNull();

    // Act
    item.onCreate();

    // Assert
    assertThat(item.getCreatedAt()).isNotNull();
    assertThat(item.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    // Arrange
    ModerationItem item = new ModerationItem();
    Instant existingTime = Instant.parse("2024-01-01T00:00:00Z");
    item.setCreatedAt(existingTime);

    // Act
    item.onCreate();

    // Assert
    assertThat(item.getCreatedAt()).isEqualTo(existingTime);
  }

  @Test
  void onCreate_setsStatusToPendingWhenNull() {
    // Arrange
    ModerationItem item = new ModerationItem();
    assertThat(item.getStatus()).isNull();

    // Act
    item.onCreate();

    // Assert
    assertThat(item.getStatus()).isEqualTo(ModerationStatus.PENDING);
  }

  @Test
  void onCreate_preservesExistingStatus() {
    // Arrange
    ModerationItem item = new ModerationItem();
    item.setStatus(ModerationStatus.APPROVED);

    // Act
    item.onCreate();

    // Assert
    assertThat(item.getStatus()).isEqualTo(ModerationStatus.APPROVED);
  }
}
