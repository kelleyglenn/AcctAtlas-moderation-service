package com.accountabilityatlas.moderationservice.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VideoSubmittedEventTest {

  @Test
  void requiresModeration_newTier_returnsTrue() {
    VideoSubmittedEvent event = createEvent("NEW");
    assertTrue(event.requiresModeration());
  }

  @ParameterizedTest(name = "trust tier ''{0}'' does not require moderation")
  @ValueSource(strings = {"TRUSTED", "MODERATOR", "ADMIN"})
  void requiresModeration_trustedTiers_returnsFalse(String trustTier) {
    VideoSubmittedEvent event = createEvent(trustTier);
    assertFalse(event.requiresModeration());
  }

  @ParameterizedTest(name = "null/unknown trust tier ''{0}'' requires moderation")
  @NullSource
  @ValueSource(strings = {"UNKNOWN", "", "new", "trusted"})
  void requiresModeration_nullOrUnknownTier_returnsTrue(String trustTier) {
    VideoSubmittedEvent event = createEvent(trustTier);
    assertTrue(
        event.requiresModeration(),
        "Missing or unrecognized trust tier should default to requiring moderation");
  }

  private VideoSubmittedEvent createEvent(String trustTier) {
    return new VideoSubmittedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        trustTier,
        "Test Video",
        Set.of("FIRST"),
        List.of(UUID.randomUUID()),
        Instant.now());
  }
}
