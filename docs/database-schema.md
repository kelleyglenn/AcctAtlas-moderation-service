# Moderation Service - Database Schema

## Overview

This document describes the database schema for the Moderation Service, focusing on JPA entity mappings and service-specific implementation details.

**Authoritative SQL Schema:** See [05-DataArchitecture.md](../../docs/05-DataArchitecture.md) for complete SQL definitions, including table creation statements, constraints, and indexes.

### Tables Owned by Moderation Service

| Table | Temporal | Description |
|-------|----------|-------------|
| `moderation.moderation_items` | Yes | Content awaiting moderation review |
| `moderation.moderation_items_history` | - | Automatic history for moderation items |
| `moderation.abuse_reports` | Yes | User-submitted abuse reports |
| `moderation.abuse_reports_history` | - | Automatic history for abuse reports |
| `moderation.audit_log` | No | Immutable log of moderation actions |

The service uses Spring Data JPA with custom handling for PostgreSQL's `tstzrange` temporal columns.

---

## JPA Entity Mappings

All entities use Lombok `@Getter` and `@Setter` annotations to reduce boilerplate. Entities also use `@NoArgsConstructor` for JPA compatibility.

### ModerationItem Entity

```java
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
```

**Notes:**
- `status` defaults to `PENDING` on creation
- `reviewerId` and `reviewedAt` are set when a moderator reviews the item
- `rejectionReason` is only populated when status is `REJECTED`
- `priority` allows queue ordering (higher priority items reviewed first)

### ContentType Enum

```java
public enum ContentType {
    VIDEO,      // Video submissions
    LOCATION    // Location submissions
}
```

### ModerationStatus Enum

```java
public enum ModerationStatus {
    PENDING,    // Awaiting review
    APPROVED,   // Approved by moderator
    REJECTED    // Rejected by moderator
}
```

### AbuseReport Entity

```java
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
```

**Notes:**
- `status` defaults to `OPEN` on creation
- `resolvedBy` and `resolution` are set when a moderator resolves the report
- `description` allows users to provide additional context (up to 2000 characters)

### AbuseReason Enum

```java
public enum AbuseReason {
    SPAM,           // Spam or advertising
    INAPPROPRIATE,  // Inappropriate or offensive content
    COPYRIGHT,      // Copyright violation
    MISINFORMATION, // False or misleading information
    OTHER           // Other reason (see description)
}
```

### ReportStatus Enum

```java
public enum ReportStatus {
    OPEN,       // Awaiting review
    RESOLVED,   // Action taken on the report
    DISMISSED   // Report dismissed (no action needed)
}
```

### AuditLogEntry Entity

```java
@Entity
@Table(name = "audit_log", schema = "moderation")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
```

**Notes:**
- Append-only table: entries are never updated or deleted
- `details` stores JSONB for flexible action-specific data (e.g., old/new values)
- `action` examples: `APPROVE`, `REJECT`, `RESOLVE_REPORT`, `DISMISS_REPORT`
- `targetType` examples: `MODERATION_ITEM`, `ABUSE_REPORT`

---

## Temporal vs Non-Temporal Decisions

| Table | Temporal | Rationale |
|-------|----------|-----------|
| `moderation_items` | Yes | Audit trail for status changes; needed for disputes and appeals |
| `abuse_reports` | Yes | Track report lifecycle; important for user trust decisions and pattern analysis |
| `audit_log` | No | Already immutable and append-only; temporal versioning would be redundant |

**Storage implications:** Temporal tables roughly double write I/O and storage for tracked tables. History tables are append-only and grow indefinitely until archived.

**Accessing history:** History tables (`moderation_items_history`, `abuse_reports_history`) are for manual SQL auditing only. The application does not expose history queries through APIs or services.

### Versioning Implementation

The service uses custom versioning trigger functions for temporal tables:

```sql
CREATE OR REPLACE FUNCTION moderation.versioning_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        INSERT INTO moderation.moderation_items_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.submitter_id,
               OLD.status, OLD.priority, OLD.reviewer_id, OLD.reviewed_at,
               OLD.rejection_reason, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        NEW.sys_period = tstzrange(NOW(), NULL);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO moderation.moderation_items_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.submitter_id,
               OLD.status, OLD.priority, OLD.reviewer_id, OLD.reviewed_at,
               OLD.rejection_reason, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;
```

This function is applied to temporal tables via triggers:

```sql
CREATE TRIGGER moderation_items_versioning
    BEFORE UPDATE OR DELETE ON moderation.moderation_items
    FOR EACH ROW EXECUTE FUNCTION moderation.versioning_trigger();

CREATE TRIGGER abuse_reports_versioning
    BEFORE UPDATE OR DELETE ON moderation.abuse_reports
    FOR EACH ROW EXECUTE FUNCTION moderation.abuse_reports_versioning_trigger();
```

---

## Index Strategy

| Index | Column(s) | Purpose |
|-------|-----------|---------|
| `idx_moderation_items_status` | `status` | Filter queue by pending/approved/rejected |
| `idx_moderation_items_content_id` | `content_id` | Look up moderation status for specific content |
| `idx_moderation_items_submitter_id` | `submitter_id` | Find items by submitter for trust tier decisions |
| `idx_moderation_items_created_at` | `created_at` | Sort queue by age (oldest first) |
| `idx_abuse_reports_status` | `status` | Filter reports by open/resolved/dismissed |
| `idx_abuse_reports_content_id` | `content_id` | Find all reports for specific content |
| `idx_abuse_reports_reporter_id` | `reporter_id` | Track reports by user (detect report abuse) |
| `idx_audit_log_actor_id` | `actor_id` | Find all actions by a specific moderator |
| `idx_audit_log_target` | `target_type, target_id` | Find all actions on a specific item |
| `idx_audit_log_created_at` | `created_at` | Time-based audit queries |

**Guidance:** Don't add indexes speculatively. Each index slows writes and consumes storage. Add only when query patterns demand it.

---

## Common Query Patterns

### Find pending moderation items (moderation queue)

```java
Page<ModerationItem> findByStatus(ModerationStatus status, Pageable pageable);
```

Uses `idx_moderation_items_status`. Typically called with `status = PENDING` and sorted by `createdAt` ascending (oldest first).

### Find pending items by content type

```java
Page<ModerationItem> findByStatusAndContentType(
    ModerationStatus status, ContentType contentType, Pageable pageable);
```

Allows moderators to focus on specific content types (e.g., only videos).

### Look up moderation status for content

```java
Optional<ModerationItem> findByContentId(UUID contentId);
```

Uses `idx_moderation_items_content_id`. Called when displaying content status or processing events.

### Count recent rejections for user

```java
@Query("SELECT COUNT(m) FROM ModerationItem m WHERE m.submitterId = :submitterId "
    + "AND m.status = 'REJECTED' AND m.reviewedAt >= :since")
int countRejectionsSince(UUID submitterId, Instant since);
```

Uses `idx_moderation_items_submitter_id`. Supports trust tier demotion logic (too many rejections = demotion).

### Calculate average review time

```java
@Query("SELECT AVG(EXTRACT(EPOCH FROM (m.reviewedAt - m.createdAt)) / 60.0) "
    + "FROM ModerationItem m WHERE m.reviewedAt IS NOT NULL")
Double calculateAverageReviewTimeMinutes();
```

Used for dashboard metrics to track moderator performance.

### Find open abuse reports

```java
Page<AbuseReport> findByStatus(ReportStatus status, Pageable pageable);
```

Uses `idx_abuse_reports_status`. Typically called with `status = OPEN`.

### Count active reports against user's content

```java
@Query("SELECT COUNT(a) FROM AbuseReport a WHERE a.contentId IN "
    + "(SELECT m.contentId FROM ModerationItem m WHERE m.submitterId = :userId) "
    + "AND a.status = 'OPEN'")
int countActiveReportsAgainst(UUID userId);
```

Supports trust tier decisions (users with many open reports may be demoted).

### Find audit log by moderator

```java
Page<AuditLogEntry> findByActorId(UUID actorId, Pageable pageable);
```

Uses `idx_audit_log_actor_id`. Review all actions taken by a specific moderator.

### Find audit log for specific target

```java
Page<AuditLogEntry> findByTargetTypeAndTargetId(
    String targetType, UUID targetId, Pageable pageable);
```

Uses `idx_audit_log_target`. View complete history of moderation actions on an item.

---

## Migration Notes

- **Flyway naming:** `V{version}__{description}.sql` (e.g., `V1__create_moderation_items.sql`)
- **Temporal table changes:** When adding columns to temporal tables, add to both main and history tables in the same migration
- **Backfilling data:** Use `sys_period` lower bound as effective date; don't add separate `created_at` columns
- **Testing migrations:** Run `./gradlew flywayMigrate` against local PostgreSQL before committing
