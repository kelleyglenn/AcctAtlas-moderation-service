package com.accountabilityatlas.moderationservice.service;

import com.accountabilityatlas.moderationservice.domain.AuditLogEntry;
import com.accountabilityatlas.moderationservice.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  @Transactional
  public void logAction(
      UUID actorId, String action, String targetType, UUID targetId, String details) {
    AuditLogEntry entry = new AuditLogEntry();
    entry.setActorId(actorId);
    entry.setAction(action);
    entry.setTargetType(targetType);
    entry.setTargetId(targetId);
    entry.setDetails(details);
    auditLogRepository.save(entry);
  }
}
