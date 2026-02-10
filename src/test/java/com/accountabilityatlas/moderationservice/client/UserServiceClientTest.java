package com.accountabilityatlas.moderationservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserServiceException;
import com.accountabilityatlas.moderationservice.client.UserServiceClient.UserSummary;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class UserServiceClientTest {

  private MockWebServer mockWebServer;
  private UserServiceClient userServiceClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = mockWebServer.url("/").toString();
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    userServiceClient = new UserServiceClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void getUser_success_returnsUserSummary() throws Exception {
    // Arrange
    UUID userId = UUID.randomUUID();
    String responseBody =
        """
        {
          "id": "%s",
          "displayName": "TestUser",
          "avatarUrl": "https://example.com/avatar.jpg",
          "trustTier": "TRUSTED",
          "stats": {
            "submissionCount": 42,
            "approvedCount": 38
          },
          "createdAt": "2024-06-15T12:00:00Z"
        }
        """
            .formatted(userId);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody));

    // Act
    Optional<UserSummary> result = userServiceClient.getUser(userId);

    // Assert
    assertThat(result).isPresent();
    UserSummary user = result.get();
    assertThat(user.id()).isEqualTo(userId);
    assertThat(user.displayName()).isEqualTo("TestUser");
    assertThat(user.avatarUrl()).isEqualTo("https://example.com/avatar.jpg");
    assertThat(user.trustTier()).isEqualTo("TRUSTED");
    assertThat(user.stats().submissionCount()).isEqualTo(42);
    assertThat(user.stats().approvedCount()).isEqualTo(38);

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/users/" + userId);
  }

  @Test
  void getUser_notFound_returnsEmpty() throws Exception {
    // Arrange
    UUID userId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("User not found"));

    // Act
    Optional<UserSummary> result = userServiceClient.getUser(userId);

    // Assert
    assertThat(result).isEmpty();
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/users/" + userId);
  }

  @Test
  void getUser_serverError_throwsException() {
    // Arrange
    UUID userId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    // Act
    Throwable thrown = catchThrowable(() -> userServiceClient.getUser(userId));

    // Assert
    assertThat(thrown).isInstanceOf(UserServiceException.class);
    assertThat(((UserServiceException) thrown).getHttpStatusCode().value()).isEqualTo(500);
  }

  @Test
  void getUser_withNullStats_returnsUserSummary() throws Exception {
    // Arrange
    UUID userId = UUID.randomUUID();
    String responseBody =
        """
        {
          "id": "%s",
          "displayName": "NewUser",
          "trustTier": "NEW",
          "stats": null
        }
        """
            .formatted(userId);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody));

    // Act
    Optional<UserSummary> result = userServiceClient.getUser(userId);

    // Assert
    assertThat(result).isPresent();
    UserSummary user = result.get();
    assertThat(user.displayName()).isEqualTo("NewUser");
    assertThat(user.trustTier()).isEqualTo("NEW");
    assertThat(user.stats()).isNull();
  }

  @Test
  void updateTrustTier_success_callsCorrectEndpoint() throws Exception {
    // Arrange
    UUID userId = UUID.randomUUID();
    String newTier = "TRUSTED";
    String reason = "Promoted after 10 approved submissions";
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{}"));

    // Act
    userServiceClient.updateTrustTier(userId, newTier, reason);

    // Assert
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
    assertThat(request.getPath()).isEqualTo("/users/" + userId + "/trust-tier");
    assertThat(request.getHeader("Content-Type")).contains("application/json");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"trustTier\":\"TRUSTED\"");
    assertThat(body).contains("\"reason\":\"Promoted after 10 approved submissions\"");
  }

  @Test
  void updateTrustTier_withNullReason_callsCorrectEndpoint() throws Exception {
    // Arrange
    UUID userId = UUID.randomUUID();
    String newTier = "MODERATOR";
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{}"));

    // Act
    userServiceClient.updateTrustTier(userId, newTier, null);

    // Assert
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
    assertThat(request.getPath()).isEqualTo("/users/" + userId + "/trust-tier");

    String body = request.getBody().readUtf8();
    assertThat(body).contains("\"trustTier\":\"MODERATOR\"");
    assertThat(body).contains("\"reason\":null");
  }

  @Test
  void updateTrustTier_notFound_throwsException() {
    // Arrange
    UUID userId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("User not found"));

    // Act
    Throwable thrown =
        catchThrowable(() -> userServiceClient.updateTrustTier(userId, "TRUSTED", null));

    // Assert
    assertThat(thrown).isInstanceOf(UserServiceException.class);
    assertThat(((UserServiceException) thrown).getHttpStatusCode().value()).isEqualTo(404);
  }

  @Test
  void updateTrustTier_forbidden_throwsException() {
    // Arrange
    UUID userId = UUID.randomUUID();
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(403).setBody("Insufficient permissions"));

    // Act
    Throwable thrown =
        catchThrowable(() -> userServiceClient.updateTrustTier(userId, "ADMIN", null));

    // Assert
    assertThat(thrown).isInstanceOf(UserServiceException.class);
    assertThat(((UserServiceException) thrown).getHttpStatusCode().value()).isEqualTo(403);
  }

  @Test
  void updateTrustTier_serverError_throwsException() {
    // Arrange
    UUID userId = UUID.randomUUID();
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    // Act
    Throwable thrown =
        catchThrowable(() -> userServiceClient.updateTrustTier(userId, "TRUSTED", null));

    // Assert
    assertThat(thrown).isInstanceOf(UserServiceException.class);
    assertThat(((UserServiceException) thrown).getHttpStatusCode().value()).isEqualTo(500);
  }
}
