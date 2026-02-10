package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.domain.ContentType;
import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.repository.ModerationItemRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stub implementation - will be completed in Task 13.
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

  private final ModerationItemRepository moderationItemRepository;
  private final AuditLogService auditLogService;

  public ModerationItem createItem(ContentType contentType, UUID contentId, UUID submitterId) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public ModerationItem getItem(UUID id) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public ModerationItem approve(UUID id, UUID reviewerId) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public ModerationItem reject(UUID id, UUID reviewerId, String reason) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
