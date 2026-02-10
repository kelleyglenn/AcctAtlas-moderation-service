package com.accountabilityatlas.moderationservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException;
import java.io.IOException;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class VideoServiceClientTest {

  private MockWebServer mockWebServer;
  private VideoServiceClient videoServiceClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = mockWebServer.url("/").toString();
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    videoServiceClient = new VideoServiceClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void updateVideoStatus_success_callsCorrectEndpoint() throws Exception {
    // Arrange
    UUID videoId = UUID.randomUUID();
    String status = "APPROVED";
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    // Act
    videoServiceClient.updateVideoStatus(videoId, status);

    // Assert
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
    assertThat(request.getPath()).isEqualTo("/internal/videos/" + videoId + "/status");
    assertThat(request.getHeader("Content-Type")).contains("application/json");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"status\":\"APPROVED\"");
  }

  @Test
  void updateVideoStatus_serverError_throwsException() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    // Act
    Throwable thrown =
        catchThrowable(() -> videoServiceClient.updateVideoStatus(videoId, "APPROVED"));

    // Assert
    assertThat(thrown).isInstanceOf(VideoServiceException.class);
    assertThat(((VideoServiceException) thrown).getHttpStatusCode().value()).isEqualTo(500);
  }

  @Test
  void updateVideoStatus_notFound_throwsException() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("Video not found"));

    // Act
    Throwable thrown =
        catchThrowable(() -> videoServiceClient.updateVideoStatus(videoId, "APPROVED"));

    // Assert
    assertThat(thrown).isInstanceOf(VideoServiceException.class);
    assertThat(((VideoServiceException) thrown).getHttpStatusCode().value()).isEqualTo(404);
  }

  @Test
  void addLocation_success_callsCorrectEndpoint() throws Exception {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    boolean isPrimary = true;
    mockWebServer.enqueue(new MockResponse().setResponseCode(201));

    // Act
    videoServiceClient.addLocation(videoId, locationId, isPrimary);

    // Assert
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/internal/videos/" + videoId + "/locations");
    assertThat(request.getHeader("Content-Type")).contains("application/json");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"locationId\":\"" + locationId + "\"");
    assertThat(body).contains("\"isPrimary\":true");
  }

  @Test
  void addLocation_serverError_throwsException() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    // Act
    Throwable thrown =
        catchThrowable(() -> videoServiceClient.addLocation(videoId, locationId, false));

    // Assert
    assertThat(thrown).isInstanceOf(VideoServiceException.class);
  }

  @Test
  void removeLocation_success_callsCorrectEndpoint() throws Exception {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    // Act
    videoServiceClient.removeLocation(videoId, locationId);

    // Assert
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("DELETE");
    assertThat(request.getPath())
        .isEqualTo("/internal/videos/" + videoId + "/locations/" + locationId);
  }

  @Test
  void removeLocation_notFound_doesNotThrow() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    // Act - should not throw
    videoServiceClient.removeLocation(videoId, locationId);

    // Assert - no exception thrown, request was made
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  void removeLocation_serverError_throwsException() {
    // Arrange
    UUID videoId = UUID.randomUUID();
    UUID locationId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    // Act
    Throwable thrown = catchThrowable(() -> videoServiceClient.removeLocation(videoId, locationId));

    // Assert
    assertThat(thrown).isInstanceOf(VideoServiceException.class);
  }
}
