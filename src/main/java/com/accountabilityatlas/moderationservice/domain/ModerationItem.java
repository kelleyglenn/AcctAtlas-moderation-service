package com.accountabilityatlas.moderationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "moderation_items", schema = "moderation")
@Getter
@Setter
@NoArgsConstructor
public class ModerationItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ContentType contentType;

  @Column(nullable = false)
  private UUID contentId;

  @Column(nullable = false)
  private UUID submitterId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModerationStatus status;

  @Column(nullable = false)
  private int priority;

  private UUID reviewerId;

  private Instant reviewedAt;

  @Column(length = 1000)
  private String rejectionReason;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = ModerationStatus.PENDING;
    }
  }
}
