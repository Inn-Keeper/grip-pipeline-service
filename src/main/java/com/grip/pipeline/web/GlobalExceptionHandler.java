package com.grip.pipeline.web;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates exceptions into {@link ApiError} bodies. Client mistakes become
 * 400 with a useful message, Spring MVC's own 4xx errors (404, 405, ...) keep
 * their status, and unexpected failures are logged with their stack trace
 * (never swallowed) and returned as an opaque 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({
      MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class,
      MethodArgumentNotValidException.class,
      IllegalArgumentException.class
  })
  public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    // Spring MVC's own exceptions (unknown path, unsupported method, ...) already
    // carry the right 4xx status and headers such as Allow; keep them.
    if (ex instanceof ErrorResponse framework) {
      HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
      if (status == HttpStatus.NOT_ACCEPTABLE) {
        // The client refused JSON, so an ApiError body cannot be written.
        return ResponseEntity.status(status).headers(framework.getHeaders()).build();
      }
      return ResponseEntity.status(status)
          .headers(framework.getHeaders())
          .body(apiError(status, framework.getBody().getDetail()));
    }
    LOG.error("Unhandled exception serving pipeline request", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  private ResponseEntity<ApiError> build(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(apiError(status, message));
  }

  private static ApiError apiError(HttpStatus status, String message) {
    return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
  }
}
