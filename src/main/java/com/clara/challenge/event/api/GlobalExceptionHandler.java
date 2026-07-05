package com.clara.challenge.event.api;

import com.clara.challenge.event.domain.EventConflictException;
import com.clara.challenge.event.domain.TraceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final Clock clock;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> details = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      details.put(error.getField(), error.getDefaultMessage());
    }
    exception
        .getBindingResult()
        .getGlobalErrors()
        .forEach(error -> details.put(error.getObjectName(), error.getDefaultMessage()));

    return error(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_ERROR,
        "Request validation failed",
        traceId(request, exception),
        details);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
  ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_ERROR,
        "Request validation failed",
        traceId(request),
        Map.of("request", exception.getMessage()));
  }

  @ExceptionHandler(TraceNotFoundException.class)
  ResponseEntity<ErrorResponse> handleTraceNotFound(
      TraceNotFoundException exception, HttpServletRequest request) {
    return error(
        HttpStatus.NOT_FOUND,
        ApiErrorCode.TRACE_NOT_FOUND,
        exception.getMessage(),
        traceId(request),
        Map.of());
  }

  @ExceptionHandler(EventConflictException.class)
  ResponseEntity<ErrorResponse> handleEventConflict(
      EventConflictException exception, HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        ApiErrorCode.EVENT_CONFLICT,
        exception.getMessage(),
        traceId(request),
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleInternalError(
      Exception exception, HttpServletRequest request) {
    log.error("Unexpected internal error for traceId={}", traceId(request), exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorCode.INTERNAL_ERROR,
        "Unexpected internal error",
        traceId(request),
        Map.of());
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status,
      ApiErrorCode code,
      String message,
      String traceId,
      Map<String, String> details) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(code, message, traceId, details, Instant.now(clock)));
  }

  private String traceId(HttpServletRequest request) {
    Object traceId = request.getAttribute("traceId");
    return traceId == null ? null : traceId.toString();
  }

  private String traceId(
      HttpServletRequest request, MethodArgumentNotValidException validationException) {
    String traceId = traceId(request);
    if (traceId != null) {
      return traceId;
    }

    Object target = validationException.getBindingResult().getTarget();
    return target instanceof EventRequest eventRequest ? eventRequest.traceId() : null;
  }
}
