package com.accountabilityatlas.moderationservice.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.accountabilityatlas.moderationservice.repository.AuditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  private AuditLogService auditLogService;

  @BeforeEach
  void setUp() {
    auditLogService = new AuditLogService(auditLogRepository);
  }

  @Test
  void logAction_validInput_savesAuditLogEntry() {
    // Arrange
    UUID actorId = UUID.randomUUID();
    String action = "APPROVE";
    String targetType = "MODERATION_ITEM";
    UUID targetId = UUID.randomUUID();
    String details = "test details";

    // Act
    auditLogService.logAction(actorId, action, targetType, targetId, details);

    // Assert
    verify(auditLogRepository)
        .save(
            argThat(
                entry ->
                    entry.getActorId().equals(actorId)
                        && entry.getAction().equals(action)
                        && entry.getTargetType().equals(targetType)
                        && entry.getTargetId().equals(targetId)));
  }
}
