package com.clara.challenge.event.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clara.challenge.event.domain.EventConflictException;
import com.clara.challenge.event.domain.IncomingEvent;
import com.clara.challenge.event.domain.TraceNotFoundException;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.service.EventIngestionResult;
import com.clara.challenge.event.service.EventIngestionService;
import com.clara.challenge.event.service.TraceStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-01-01T10:03:00Z");

  @Mock private EventIngestionService eventIngestionService;
  @Mock private TraceStatusService traceStatusService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new EventController(eventIngestionService, traceStatusService))
            .setControllerAdvice(new GlobalExceptionHandler(clock))
            .build();
  }

  @Test
  void shouldReturnCreatedStatus_WhenNewEventIsAccepted() throws Exception {
    when(eventIngestionService.ingest(any(IncomingEvent.class)))
        .thenReturn(new EventIngestionResult(startedState(), false));

    mockMvc
        .perform(post("/v1/events").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.traceId").value("trace-1"))
        .andExpect(jsonPath("$.status").value("STARTED"))
        .andExpect(jsonPath("$.lastEventName").value("payment-created"))
        .andExpect(jsonPath("$.lastEventResult").value("SUCCESS"))
        .andExpect(jsonPath("$.eventsReceived").value(1));
  }

  @Test
  void shouldReturnOkStatus_WhenDuplicateEventIsIdempotent() throws Exception {
    when(eventIngestionService.ingest(any(IncomingEvent.class)))
        .thenReturn(new EventIngestionResult(startedState(), true));

    mockMvc
        .perform(post("/v1/events").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.traceId").value("trace-1"));
  }

  @Test
  void shouldReturnTraceStatus() throws Exception {
    when(traceStatusService.getStatus("trace-1")).thenReturn(waitingState());

    mockMvc
        .perform(get("/v1/traces/trace-1/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.traceId").value("trace-1"))
        .andExpect(jsonPath("$.status").value("WAITING_OTHER_EVENT"))
        .andExpect(jsonPath("$.nextExpectedEvent").value("payment-confirmed"))
        .andExpect(jsonPath("$.nextExpectedBefore").value("2026-01-01T10:01:00Z"));
  }

  @Test
  void shouldReturnBadRequest_WhenRequestValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"traceId\":\"trace-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("Request validation failed"))
        .andExpect(jsonPath("$.details.eventId").value("must not be blank"));
  }

  @Test
  void shouldReturnConflict_WhenEventTransitionIsRejected() throws Exception {
    when(eventIngestionService.ingest(any(IncomingEvent.class)))
        .thenThrow(new EventConflictException("Unexpected event. Expected payment-confirmed"));

    mockMvc
        .perform(post("/v1/events").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_CONFLICT"))
        .andExpect(jsonPath("$.traceId").value("trace-1"))
        .andExpect(jsonPath("$.message").value("Unexpected event. Expected payment-confirmed"));
  }

  @Test
  void shouldReturnNotFound_WhenTraceDoesNotExist() throws Exception {
    when(traceStatusService.getStatus("missing-trace"))
        .thenThrow(new TraceNotFoundException("missing-trace"));

    mockMvc
        .perform(get("/v1/traces/missing-trace/status"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACE_NOT_FOUND"))
        .andExpect(jsonPath("$.traceId").value("missing-trace"))
        .andExpect(jsonPath("$.message").value("Trace not found: missing-trace"));
  }

  @Test
  void shouldReturnTraceId_WhenRequestValidationFailsAfterParsing() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace("\"eventName\": \"payment-created\",", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.traceId").value("trace-1"))
        .andExpect(jsonPath("$.details.eventName").value("must not be blank"));
  }

  @Test
  void shouldReturnBadRequest_WhenJsonCannotBeRead() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace("SUCCESS", "UNKNOWN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.request", containsString("UNKNOWN")));
  }

  private static String validRequest() {
    return """
           {
             "eventId": "event-1",
             "traceId": "trace-1",
             "eventName": "payment-created",
             "result": "SUCCESS",
             "occurredAt": "2026-01-01T10:00:00Z",
             "metadata": {"source": "checkout"}
           }
           """;
  }

  private static TraceState startedState() {
    return new TraceState(
        "trace-1",
        com.clara.challenge.event.domain.TraceStatus.STARTED,
        "event-1",
        "payment-created",
        com.clara.challenge.event.domain.EventResult.SUCCESS,
        OCCURRED_AT,
        null,
        null,
        1,
        null,
        null);
  }

  private static TraceState waitingState() {
    return new TraceState(
        "trace-1",
        com.clara.challenge.event.domain.TraceStatus.WAITING_OTHER_EVENT,
        "event-1",
        "payment-created",
        com.clara.challenge.event.domain.EventResult.SUCCESS,
        OCCURRED_AT,
        "payment-confirmed",
        OCCURRED_AT.plusSeconds(60),
        1,
        null,
        null);
  }
}
