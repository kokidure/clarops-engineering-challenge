package com.clara.challenge.event.api;

import com.clara.challenge.event.domain.IncomingEvent;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.service.EventIngestionResult;
import com.clara.challenge.event.service.EventIngestionService;
import com.clara.challenge.event.service.TraceStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class EventController {

  private final EventIngestionService eventIngestionService;
  private final TraceStatusService traceStatusService;

  @PostMapping("/events")
  public ResponseEntity<TraceStatusResponse> ingestEvent(
      @Valid @RequestBody EventRequest request, HttpServletRequest servletRequest) {
    servletRequest.setAttribute("traceId", request.traceId());
    EventIngestionResult result = eventIngestionService.ingest(toDomain(request));
    HttpStatus status = result.idempotentDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;

    return ResponseEntity.status(status).body(toResponse(result.traceState()));
  }

  @GetMapping("/traces/{traceId}/status")
  public TraceStatusResponse getTraceStatus(
      @PathVariable String traceId, HttpServletRequest request) {
    request.setAttribute("traceId", traceId);
    return toResponse(traceStatusService.getStatus(traceId));
  }

  private IncomingEvent toDomain(EventRequest request) {
    return new IncomingEvent(
        request.eventId(),
        request.traceId(),
        request.eventName(),
        com.clara.challenge.event.domain.EventResult.valueOf(request.result().name()),
        request.occurredAt(),
        request.nextExpectedEvent(),
        request.nextEventTtlSeconds(),
        request.finalEvent(),
        request.metadata());
  }

  private TraceStatusResponse toResponse(TraceState state) {
    return new TraceStatusResponse(
        state.traceId(),
        TraceStatus.valueOf(state.status().name()),
        state.lastEventName(),
        EventResult.valueOf(state.lastEventResult().name()),
        state.nextExpectedEvent(),
        state.nextExpectedBefore(),
        state.eventsReceived());
  }
}
