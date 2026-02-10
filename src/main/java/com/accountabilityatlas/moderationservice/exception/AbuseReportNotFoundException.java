package com.accountabilityatlas.moderationservice.exception;

import java.util.UUID;

public class AbuseReportNotFoundException extends RuntimeException {

  public AbuseReportNotFoundException(UUID id) {
    super("Abuse report not found: " + id);
  }
}
