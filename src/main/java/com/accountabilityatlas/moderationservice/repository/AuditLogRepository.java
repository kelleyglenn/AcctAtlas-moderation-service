package com.accountabilityatlas.moderationservice.repository;

import com.accountabilityatlas.moderationservice.domain.AuditLogEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

  Page<AuditLogEntry> findByActorId(UUID actorId, Pageable pageable);

  Page<AuditLogEntry> findByTargetTypeAndTargetId(
      String targetType, UUID targetId, Pageable pageable);
}
