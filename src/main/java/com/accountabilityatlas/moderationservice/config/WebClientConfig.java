package com.accountabilityatlas.moderationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient videoServiceWebClient(
      WebClient.Builder builder, @Value("${app.video-service.base-url}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }

  @Bean
  public WebClient userServiceWebClient(
      WebClient.Builder builder, @Value("${app.user-service.base-url}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }
}
