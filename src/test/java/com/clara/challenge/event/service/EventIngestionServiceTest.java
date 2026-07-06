package com.clara.challenge.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.challenge.event.domain.EventConflictException;
import com.clara.challenge.event.domain.EventResult;
import com.clara.challenge.event.domain.IncomingEvent;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.domain.TraceStatus;
import com.clara.challenge.event.domain.TransitionReason;
import com.clara.challenge.event.persistence.EventEntity;
import com.clara.challenge.event.persistence.EventRepository;
import com.clara.challenge.event.persistence.TraceStateEntity;
import com.clara.challenge.event.persistence.TraceStateRepository;
import com.clara.challenge.event.persistence.TraceStatusAuditEntity;
import com.clara.challenge.event.persistence.TraceStatusAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class EventIngestionServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-01-01T10:02:01Z");

  @Mock private EventRepository eventRepository;
  @Mock private TraceStateRepository traceStateRepository;
  @Mock private TraceStatusAuditRepository traceStatusAuditRepository;
  @Mock private TraceStatusService traceStatusService;
  @Mock private PlatformTransactionManager transactionManager;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private EventIngestionService service;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(ArgumentMatchers.any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    service =
        new EventIngestionService(
            eventRepository,
            traceStateRepository,
            traceStatusAuditRepository,
            traceStatusService,
            transactionManager,
            clock);
  }

  @Test
  void shouldPersistEventStateAndAudit_WhenFirstEventIsAccepted() {
    IncomingEvent event = eventWaitingFor("event-1", "payment-created", "payment-confirmed", 60);
    when(eventRepository.findByEventId(event.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.lockByTraceId(event.traceId())).thenReturn(Optional.empty());

    EventIngestionResult result = service.ingest(event);

    assertThat(result.idempotentDuplicate()).isFalse();
    assertThat(result.traceState().status()).isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(result.traceState().nextExpectedBefore()).isEqualTo(OCCURRED_AT.plusSeconds(60));

    ArgumentCaptor<EventEntity> eventCaptor = ArgumentCaptor.forClass(EventEntity.class);
    ArgumentCaptor<TraceStateEntity> stateCaptor = ArgumentCaptor.forClass(TraceStateEntity.class);
    ArgumentCaptor<TraceStatusAuditEntity> auditCaptor =
        ArgumentCaptor.forClass(TraceStatusAuditEntity.class);

    verify(eventRepository).saveAndFlush(eventCaptor.capture());
    verify(traceStateRepository).save(stateCaptor.capture());
    verify(traceStatusAuditRepository).save(auditCaptor.capture());

    assertThat(eventCaptor.getValue().toDomain()).isEqualTo(event);
    assertThat(stateCaptor.getValue().toDomain()).isEqualTo(result.traceState());
    assertThat(auditCaptor.getValue().getPreviousStatus()).isNull();
    assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(auditCaptor.getValue().getReason()).isEqualTo(TransitionReason.NEXT_EVENT_EXPECTED);
    assertThat(auditCaptor.getValue().getEventId()).isEqualTo("event-1");
  }

  @Test
  void shouldUpdateStateAndWriteAudit_WhenExpectedEventIsAccepted() {
    IncomingEvent event = event("event-2", "payment-confirmed");
    TraceStateEntity currentState =
        TraceStateEntity.from(waitingState(OCCURRED_AT.plusSeconds(60)));
    when(eventRepository.findByEventId(event.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.lockByTraceId(event.traceId())).thenReturn(Optional.of(currentState));

    EventIngestionResult result = service.ingest(event);

    assertThat(result.idempotentDuplicate()).isFalse();
    assertThat(result.traceState().status()).isEqualTo(TraceStatus.STARTED);
    assertThat(result.traceState().lastEventId()).isEqualTo("event-2");
    assertThat(result.traceState().eventsReceived()).isEqualTo(2);

    ArgumentCaptor<TraceStatusAuditEntity> auditCaptor =
        ArgumentCaptor.forClass(TraceStatusAuditEntity.class);
    verify(eventRepository).saveAndFlush(any(EventEntity.class));
    verify(traceStateRepository).save(currentState);
    verify(traceStatusAuditRepository).save(auditCaptor.capture());

    assertThat(currentState.toDomain()).isEqualTo(result.traceState());
    assertThat(auditCaptor.getValue().getPreviousStatus())
        .isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo(TraceStatus.STARTED);
    assertThat(auditCaptor.getValue().getReason())
        .isEqualTo(TransitionReason.EXPECTED_EVENT_RECEIVED);
  }

  @Test
  void shouldReturnCurrentStateWithoutMutating_WhenDuplicateEventIsEquivalent() {
    IncomingEvent event = event("event-1", "payment-created");
    TraceState currentState = startedState();
    when(eventRepository.findByEventId(event.eventId()))
        .thenReturn(Optional.of(EventEntity.from(event, OCCURRED_AT.plusSeconds(1))));
    when(traceStateRepository.lockByTraceId(event.traceId()))
        .thenReturn(Optional.of(TraceStateEntity.from(currentState)));
    when(traceStatusService.expireIfNeeded(any(TraceStateEntity.class))).thenReturn(currentState);

    EventIngestionResult result = service.ingest(event);

    assertThat(result.idempotentDuplicate()).isTrue();
    assertThat(result.traceState()).isEqualTo(currentState);
    verify(eventRepository, never()).save(any(EventEntity.class));
    verify(traceStateRepository, never()).save(any(TraceStateEntity.class));
    verify(traceStatusAuditRepository, never()).save(any(TraceStatusAuditEntity.class));
  }

  @Test
  void shouldReturnCurrentStateWithoutMutating_WhenConcurrentInsertFindsDuplicateEvent() {
    IncomingEvent event = event("event-1", "payment-created");
    TraceState currentState = startedState();
    EventEntity existingEvent = EventEntity.from(event, OCCURRED_AT.plusSeconds(1));
    when(eventRepository.findByEventId(event.eventId()))
        .thenReturn(Optional.empty(), Optional.of(existingEvent));
    when(traceStateRepository.lockByTraceId(event.traceId()))
        .thenReturn(Optional.empty(), Optional.of(TraceStateEntity.from(currentState)));
    when(eventRepository.saveAndFlush(any(EventEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate event_id"));
    when(traceStatusService.expireIfNeeded(any(TraceStateEntity.class))).thenReturn(currentState);

    EventIngestionResult result = service.ingest(event);

    assertThat(result.idempotentDuplicate()).isTrue();
    assertThat(result.traceState()).isEqualTo(currentState);
    verify(traceStateRepository, never()).save(any(TraceStateEntity.class));
    verify(traceStatusAuditRepository, never()).save(any(TraceStatusAuditEntity.class));
  }

  @Test
  void shouldRejectDuplicateEvent_WhenPayloadDiffers() {
    IncomingEvent existingEvent = event("event-1", "payment-created");
    IncomingEvent incomingEvent = event("event-1", "payment-cancelled");
    when(eventRepository.findByEventId(incomingEvent.eventId()))
        .thenReturn(Optional.of(EventEntity.from(existingEvent, OCCURRED_AT.plusSeconds(1))));

    assertThatThrownBy(() -> service.ingest(incomingEvent))
        .isInstanceOf(EventConflictException.class)
        .hasMessage("Duplicate eventId has different payload");

    verify(traceStateRepository, never()).save(any(TraceStateEntity.class));
    verify(traceStatusAuditRepository, never()).save(any(TraceStatusAuditEntity.class));
  }

  @Test
  void shouldExpireTrace_WhenDuplicateEventIsEquivalentAndTraceIsPastDeadline() {
    IncomingEvent event = event("event-1", "payment-created");
    TraceState expiredState =
        new TraceState(
            "trace-1",
            TraceStatus.TTL_EXPIRED_FOR_EVENT,
            "event-1",
            "payment-created",
            EventResult.SUCCESS,
            OCCURRED_AT,
            null,
            null,
            1,
            null,
            NOW);
    TraceStateEntity stateEntity = TraceStateEntity.from(waitingState(OCCURRED_AT.plusSeconds(60)));
    when(eventRepository.findByEventId(event.eventId()))
        .thenReturn(Optional.of(EventEntity.from(event, OCCURRED_AT.plusSeconds(1))));
    when(traceStateRepository.lockByTraceId(event.traceId())).thenReturn(Optional.of(stateEntity));
    when(traceStatusService.expireIfNeeded(stateEntity)).thenReturn(expiredState);

    EventIngestionResult result = service.ingest(event);

    assertThat(result.idempotentDuplicate()).isTrue();
    assertThat(result.traceState()).isEqualTo(expiredState);
    verify(traceStatusService).expireIfNeeded(stateEntity);
  }

  private static IncomingEvent event(String eventId, String eventName) {
    return new IncomingEvent(
        eventId,
        "trace-1",
        eventName,
        EventResult.SUCCESS,
        OCCURRED_AT,
        null,
        null,
        false,
        Map.of("source", "checkout"));
  }

  private static IncomingEvent eventWaitingFor(
      String eventId, String eventName, String nextExpectedEvent, int ttlSeconds) {
    return new IncomingEvent(
        eventId,
        "trace-1",
        eventName,
        EventResult.SUCCESS,
        OCCURRED_AT,
        nextExpectedEvent,
        ttlSeconds,
        false,
        Map.of("source", "checkout"));
  }

  private static TraceState startedState() {
    return new TraceState(
        "trace-1",
        TraceStatus.STARTED,
        "event-1",
        "payment-created",
        EventResult.SUCCESS,
        OCCURRED_AT,
        null,
        null,
        1,
        null,
        null);
  }

  private static TraceState waitingState(Instant nextExpectedBefore) {
    return new TraceState(
        "trace-1",
        TraceStatus.WAITING_OTHER_EVENT,
        "event-1",
        "payment-created",
        EventResult.SUCCESS,
        OCCURRED_AT,
        "payment-confirmed",
        nextExpectedBefore,
        1,
        null,
        null);
  }
}
