package com.clara.challenge.event.api;

import com.clara.challenge.event.domain.EventResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record EventRequest(
    @NotBlank @Size(max = 120) String eventId,
    @NotBlank @Size(max = 120) String traceId,
    @NotBlank @Size(max = 120) String eventName,
    @NotNull EventResult result,
    @NotNull Instant occurredAt,
    @Size(max = 120) String nextExpectedEvent,
    @Positive Integer nextEventTtlSeconds,
    Boolean finalEvent,
    Map<String, Object> metadata) {

  public EventRequest {
    finalEvent = finalEvent != null && finalEvent;
  }

  @JsonIgnore
  @AssertTrue(message = "nextExpectedEvent must not be blank when provided")
  public boolean isValidNextExpectedEventName() {
    return nextExpectedEvent == null || !nextExpectedEvent.isBlank();
  }

  @JsonIgnore
  @AssertTrue(message = "nextExpectedEvent and nextEventTtlSeconds must be provided together")
  public boolean isCompleteNextExpectedEvent() {
    return (nextExpectedEvent != null) == (nextEventTtlSeconds != null);
  }

  @JsonIgnore
  @AssertTrue(message = "finalEvent cannot define nextExpectedEvent or nextEventTtlSeconds")
  public boolean isValidFinalEventFields() {
    return !Boolean.TRUE.equals(finalEvent)
        || (nextExpectedEvent == null && nextEventTtlSeconds == null);
  }
}
