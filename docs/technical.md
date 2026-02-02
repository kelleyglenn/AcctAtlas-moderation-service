# Moderation Service - Technical Documentation

## Service Overview

The Moderation Service manages the content quality control workflow for AccountabilityAtlas. It handles the moderation queue, content approval/rejection, abuse reporting, and trust tier progression logic.

## Responsibilities

- Moderation queue management
- Content approval/rejection workflow
- Auto-approval for trusted users
- Trust tier promotion logic execution
- Abuse report handling
- Moderation audit trail

## Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.2.x |
| Language | Java 21 |
| Build | Gradle |
| Database | PostgreSQL 15 |

## Dependencies

- **PostgreSQL**: Moderation queue, abuse reports, audit log
- **user-service**: Trust tier queries and updates
- **video-service**: Content details
- **SQS**: Event consumption and publishing

## Documentation Index

| Document | Status | Description |
|----------|--------|-------------|
| [api-specification.yaml](api-specification.yaml) | Complete | OpenAPI 3.1 specification |
| [database-schema.md](database-schema.md) | Planned | Schema documentation |
| [moderation-workflow.md](moderation-workflow.md) | Planned | Workflow diagrams |
| [trust-progression.md](trust-progression.md) | Planned | Trust tier rules |

## Domain Model

```
ModerationItem (temporal - sys_period tracks history)
├── id: UUID
├── contentType: ContentType (VIDEO, LOCATION)
├── contentId: UUID
├── submitterId: UUID
├── status: ModerationStatus (PENDING, APPROVED, REJECTED)
├── priority: int
├── reviewerId: UUID (nullable)
├── reviewedAt: Instant (nullable)
├── rejectionReason: String (nullable)
└── sysPeriod: tstzrange  // lower bound = created, NULL upper = current

AbuseReport (temporal - sys_period tracks history)
├── id: UUID
├── contentType: ContentType
├── contentId: UUID
├── reporterId: UUID
├── reason: AbuseReason (SPAM, INAPPROPRIATE, COPYRIGHT, MISINFORMATION, OTHER)
├── description: String
├── status: ReportStatus (OPEN, RESOLVED, DISMISSED)
├── resolvedBy: UUID (nullable)
├── resolution: String (nullable)
└── sysPeriod: tstzrange  // lower bound = created, NULL upper = current

AuditLogEntry (non-temporal - append-only/immutable)
├── id: UUID
├── actorId: UUID
├── action: String
├── targetType: String
├── targetId: UUID
├── details: JsonObject
└── createdAt: Instant
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /moderation/queue | Moderator | Get pending items |
| GET | /moderation/queue/{id} | Moderator | Get item details |
| POST | /moderation/queue/{id}/approve | Moderator | Approve content |
| POST | /moderation/queue/{id}/reject | Moderator | Reject with reason |
| GET | /moderation/queue/stats | Moderator | Queue statistics |
| GET | /moderation/reports | Moderator | Get abuse reports |
| POST | /moderation/reports | User | Submit abuse report |
| GET | /moderation/reports/{id} | Moderator | Get report details |
| POST | /moderation/reports/{id}/resolve | Moderator | Resolve report |
| POST | /moderation/reports/{id}/dismiss | Moderator | Dismiss report |

## Query Parameters (GET /moderation/queue)

| Parameter | Type | Description |
|-----------|------|-------------|
| status | String | Filter by status (default: PENDING) |
| contentType | String | Filter by content type |
| sortBy | String | Sort by: createdAt, priority |
| page | Int | Page number |
| size | Int | Page size |

## Moderation Workflow

```
┌─────────────────┐
│ VideoSubmitted  │
│    (Event)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Check Submitter │
│   Trust Tier    │
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────────┐
│TRUSTED│ │    NEW    │
│  or   │ │           │
│higher │ │           │
└───┬───┘ └─────┬─────┘
    │           │
    │           ▼
    │     ┌───────────┐
    │     │Add to Mod │
    │     │   Queue   │
    │     └─────┬─────┘
    │           │
    │     ┌─────┴─────┐
    │     ▼           ▼
    │ ┌───────┐  ┌────────┐
    │ │Approve│  │ Reject │
    │ └───┬───┘  └────┬───┘
    │     │           │
    ▼     ▼           ▼
┌─────────────┐  ┌─────────────┐
│VideoApproved│  │VideoRejected│
│   (Event)   │  │   (Event)   │
└─────────────┘  └─────────────┘
```

## Trust Tier Progression

Automatic promotion from NEW to TRUSTED:

```java
public boolean checkTrustPromotion(UUID userId) {
    User user = userService.getUser(userId);
    UserStats stats = userService.getUserStats(userId);

    if (user.getTrustTier() != TrustTier.NEW) {
        return false;
    }

    // Account creation time derived from lower bound of sys_period
    Duration accountAge = Duration.between(user.getCreatedAt(), Instant.now());
    int recentRejections = moderationRepository
        .countRejectionsSince(userId, Instant.now().minus(30, ChronoUnit.DAYS));
    int activeReports = abuseReportRepository
        .countActiveReportsAgainst(userId);

    return accountAge.toDays() >= 30
        && stats.getApprovedCount() >= 10
        && recentRejections == 0
        && activeReports == 0;
}
```

## Events Consumed

| Event | Action |
|-------|--------|
| VideoSubmitted | Create moderation item (or auto-approve) |
| UserTrustTierChanged | Re-evaluate pending items |

## Events Published

| Event | Trigger | Consumers |
|-------|---------|-----------|
| VideoApproved | Content approved | video-service, search-service |
| VideoRejected | Content rejected | video-service, notification-service |

## Local Development

```bash
# Start dependencies
docker-compose up -d postgres

# Run migrations
./gradlew flywayMigrate

# Run service
./gradlew bootRun

# Service available at http://localhost:8085
```
