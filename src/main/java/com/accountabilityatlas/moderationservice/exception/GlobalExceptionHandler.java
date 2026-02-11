package com.accountabilityatlas.moderationservice.exception;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(
      String code, String message, List<FieldError> details, String traceId) {}

  public record FieldError(String field, String message) {}

  @ExceptionHandler(ModerationItemNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleModerationItemNotFound(
      ModerationItemNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(AbuseReportNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAbuseReportNotFound(AbuseReportNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(ItemAlreadyReviewedException.class)
  public ResponseEntity<ErrorResponse> handleItemAlreadyReviewed(ItemAlreadyReviewedException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ErrorResponse(
                "ALREADY_REVIEWED", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<FieldError> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                details,
                UUID.randomUUID().toString()));
  }

  @ExceptionHandler(StatusNotAllowedException.class)
  public ResponseEntity<ErrorResponse> handleStatusNotAllowed(StatusNotAllowedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            new ErrorResponse(
                "STATUS_NOT_ALLOWED", ex.getMessage(), null, UUID.randomUUID().toString()));
  }

  @ExceptionHandler(
      com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException
          .class)
  public ResponseEntity<ErrorResponse> handleVideoServiceException(
      com.accountabilityatlas.moderationservice.client.VideoServiceClient.VideoServiceException
          ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ErrorResponse(
                "VIDEO_SERVICE_ERROR", ex.getMessage(), null, UUID.randomUUID().toString()));
  }
}
