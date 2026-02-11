package com.accountabilityatlas.moderationservice.repository;

import com.accountabilityatlas.moderationservice.domain.ContentType;
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

  Page<ModerationItem> findByStatusAndContentType(
      ModerationStatus status, ContentType contentType, Pageable pageable);

  Optional<ModerationItem> findByContentId(UUID contentId);

  java.util.List<ModerationItem> findBySubmitterIdAndStatus(UUID submitterId, ModerationStatus status);

  @Query(
      "SELECT COUNT(m) FROM ModerationItem m WHERE m.submitterId = :submitterId "
          + "AND m.status = 'REJECTED' AND m.reviewedAt >= :since")
  int countRejectionsSince(UUID submitterId, Instant since);

  long countByStatus(ModerationStatus status);

  long countByStatusAndReviewedAtGreaterThanEqual(ModerationStatus status, Instant since);

  @Query(
      nativeQuery = true,
      value =
          "SELECT AVG(EXTRACT(EPOCH FROM (reviewed_at - created_at)) / 60.0) "
              + "FROM moderation.moderation_items WHERE reviewed_at IS NOT NULL")
  Double calculateAverageReviewTimeMinutes();
}
