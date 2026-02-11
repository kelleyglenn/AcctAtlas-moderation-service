package com.accountabilityatlas.moderationservice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

@ExtendWith(MockitoExtension.class)
class ModerationEventPublisherTest {

  @Mock private StreamBridge streamBridge;

  @Captor private ArgumentCaptor<Object> eventCaptor;

  private ModerationEventPublisher moderationEventPublisher;

  @BeforeEach
  void setUp() {
    moderationEventPublisher = new ModerationEventPublisher(streamBridge);
  }

  @Test
  void publishVideoApproved_sendsToStreamBridge() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    when(streamBridge.send(eq("moderationEvent-out-0"), any())).thenReturn(true);

    // Act
    moderationEventPublisher.publishVideoApproved(videoId, reviewerId);

    // Assert
    verify(streamBridge).send(eq("moderationEvent-out-0"), eventCaptor.capture());
    Object capturedEvent = eventCaptor.getValue();

    assertThat(capturedEvent).isInstanceOf(VideoApprovedEvent.class);
    VideoApprovedEvent approvedEvent = (VideoApprovedEvent) capturedEvent;
    assertThat(approvedEvent.videoId()).isEqualTo(videoId);
    assertThat(approvedEvent.reviewerId()).isEqualTo(reviewerId);
    assertThat(approvedEvent.timestamp()).isNotNull();
  }

  @Test
  void publishVideoRejected_sendsToStreamBridge() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String reason = "Inappropriate content";
    when(streamBridge.send(eq("moderationEvent-out-0"), any())).thenReturn(true);

    // Act
    moderationEventPublisher.publishVideoRejected(videoId, reviewerId, reason);

    // Assert
    verify(streamBridge).send(eq("moderationEvent-out-0"), eventCaptor.capture());
    Object capturedEvent = eventCaptor.getValue();

    assertThat(capturedEvent).isInstanceOf(VideoRejectedEvent.class);
    VideoRejectedEvent rejectedEvent = (VideoRejectedEvent) capturedEvent;
    assertThat(rejectedEvent.videoId()).isEqualTo(videoId);
    assertThat(rejectedEvent.reviewerId()).isEqualTo(reviewerId);
    assertThat(rejectedEvent.reason()).isEqualTo(reason);
    assertThat(rejectedEvent.timestamp()).isNotNull();
  }
}
