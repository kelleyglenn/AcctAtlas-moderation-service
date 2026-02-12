package com.accountabilityatlas.moderationservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModerationEventPublisherTest {

  private static final String MODERATION_EVENTS_QUEUE = "moderation-events";

  @Mock private SqsTemplate sqsTemplate;

  @Captor private ArgumentCaptor<Object> eventCaptor;

  @InjectMocks private ModerationEventPublisher moderationEventPublisher;

  @Test
  void publishVideoApproved_sendsToSqs() {
    // Arrange
    ReflectionTestUtils.setField(
        moderationEventPublisher, "moderationEventsQueue", MODERATION_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    // Act
    moderationEventPublisher.publishVideoApproved(videoId, reviewerId);

    // Assert
    verify(sqsTemplate).send(eq(MODERATION_EVENTS_QUEUE), eventCaptor.capture());
    Object capturedEvent = eventCaptor.getValue();

    assertThat(capturedEvent).isInstanceOf(VideoApprovedEvent.class);
    VideoApprovedEvent approvedEvent = (VideoApprovedEvent) capturedEvent;
    assertThat(approvedEvent.videoId()).isEqualTo(videoId);
    assertThat(approvedEvent.reviewerId()).isEqualTo(reviewerId);
    assertThat(approvedEvent.timestamp()).isNotNull();
  }

  @Test
  void publishVideoRejected_sendsToSqs() {
    // Arrange
    ReflectionTestUtils.setField(
        moderationEventPublisher, "moderationEventsQueue", MODERATION_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String reason = "Inappropriate content";

    // Act
    moderationEventPublisher.publishVideoRejected(videoId, reviewerId, reason);

    // Assert
    verify(sqsTemplate).send(eq(MODERATION_EVENTS_QUEUE), eventCaptor.capture());
    Object capturedEvent = eventCaptor.getValue();

    assertThat(capturedEvent).isInstanceOf(VideoRejectedEvent.class);
    VideoRejectedEvent rejectedEvent = (VideoRejectedEvent) capturedEvent;
    assertThat(rejectedEvent.videoId()).isEqualTo(videoId);
    assertThat(rejectedEvent.reviewerId()).isEqualTo(reviewerId);
    assertThat(rejectedEvent.reason()).isEqualTo(reason);
    assertThat(rejectedEvent.timestamp()).isNotNull();
  }

  @Test
  void publishVideoApproved_sqsFailure_rethrowsException() {
    // Arrange
    ReflectionTestUtils.setField(
        moderationEventPublisher, "moderationEventsQueue", MODERATION_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    RuntimeException sqsException = new RuntimeException("SQS connection failed");
    when(sqsTemplate.send(eq(MODERATION_EVENTS_QUEUE), any(VideoApprovedEvent.class)))
        .thenThrow(sqsException);

    // Act & Assert
    assertThatThrownBy(() -> moderationEventPublisher.publishVideoApproved(videoId, reviewerId))
        .isSameAs(sqsException);
  }

  @Test
  void publishVideoRejected_sqsFailure_rethrowsException() {
    // Arrange
    ReflectionTestUtils.setField(
        moderationEventPublisher, "moderationEventsQueue", MODERATION_EVENTS_QUEUE);
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String reason = "Inappropriate content";

    RuntimeException sqsException = new RuntimeException("SQS connection failed");
    when(sqsTemplate.send(eq(MODERATION_EVENTS_QUEUE), any(VideoRejectedEvent.class)))
        .thenThrow(sqsException);

    // Act & Assert
    assertThatThrownBy(
            () -> moderationEventPublisher.publishVideoRejected(videoId, reviewerId, reason))
        .isSameAs(sqsException);
  }
}
