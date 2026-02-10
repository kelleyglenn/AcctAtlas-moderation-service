package com.accountabilityatlas.moderationservice.repository;

import com.accountabilityatlas.moderationservice.domain.ModerationItem;
import com.accountabilityatlas.moderationservice.domain.ModerationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationItemRepository extends JpaRepository<ModerationItem, UUID> {

  Page<ModerationItem> findByStatus(ModerationStatus status, Pageable pageable);

  Optional<ModerationItem> findByContentId(UUID contentId);

  @Query("SELECT COUNT(m) FROM ModerationItem m WHERE m.submitterId = :submitterId "
      + "AND m.status = 'REJECTED' AND m.reviewedAt >= :since")
  int countRejectionsSince(UUID submitterId, Instant since);

  long countByStatus(ModerationStatus status);
}
