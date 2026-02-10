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
@Table(name = "abuse_reports", schema = "moderation")
@Getter
@Setter
@NoArgsConstructor
public class AbuseReport {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ContentType contentType;

  @Column(nullable = false)
  private UUID contentId;

  @Column(nullable = false)
  private UUID reporterId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AbuseReason reason;

  @Column(length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportStatus status;

  private UUID resolvedBy;

  @Column(length = 1000)
  private String resolution;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = ReportStatus.OPEN;
    }
  }
}
