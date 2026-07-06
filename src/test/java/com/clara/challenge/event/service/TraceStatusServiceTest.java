package com.clara.challenge.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.challenge.event.domain.EventResult;
import com.clara.challenge.event.domain.TraceNotFoundException;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.domain.TraceStatus;
import com.clara.challenge.event.domain.TransitionReason;
import com.clara.challenge.event.persistence.TraceStateEntity;
import com.clara.challenge.event.persistence.TraceStateRepository;
import com.clara.challenge.event.persistence.TraceStatusAuditEntity;
import com.clara.challenge.event.persistence.TraceStatusAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceStatusServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-01-01T10:02:01Z");

  @Mock private TraceStateRepository traceStateRepository;
  @Mock private TraceStatusAuditRepository traceStatusAuditRepository;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private TraceStatusService service;

  @BeforeEach
  void setUp() {
    service = new TraceStatusService(traceStateRepository, traceStatusAuditRepository, clock);
  }

  @Test
  void shouldReturnCurrentStateWithoutMutating_WhenTraceIsNotExpired() {
    TraceState currentState = waitingState(OCCURRED_AT.plusSeconds(180));
    TraceStateEntity stateEntity = TraceStateEntity.from(currentState);
    when(traceStateRepository.lockByTraceId("trace-1")).thenReturn(Optional.of(stateEntity));

    TraceState result = service.getStatus("trace-1");

    assertThat(result).isEqualTo(currentState);
    verify(traceStateRepository, never()).save(any(TraceStateEntity.class));
    verify(traceStatusAuditRepository, never()).save(any(TraceStatusAuditEntity.class));
  }

  @Test
  void shouldPersistExpirationAndAudit_WhenWaitingTraceIsPastDeadline() {
    TraceState currentState = waitingState(OCCURRED_AT.plusSeconds(60));
    TraceStateEntity stateEntity = TraceStateEntity.from(currentState);
    when(traceStateRepository.lockByTraceId("trace-1")).thenReturn(Optional.of(stateEntity));

    TraceState result = service.getStatus("trace-1");

    assertThat(result.status()).isEqualTo(TraceStatus.TTL_EXPIRED_FOR_EVENT);
    assertThat(result.expiredAt()).isEqualTo(NOW);
    assertThat(stateEntity.toDomain()).isEqualTo(result);
    verify(traceStateRepository).save(stateEntity);

    ArgumentCaptor<TraceStatusAuditEntity> auditCaptor =
        ArgumentCaptor.forClass(TraceStatusAuditEntity.class);
    verify(traceStatusAuditRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getPreviousStatus())
        .isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo(TraceStatus.TTL_EXPIRED_FOR_EVENT);
    assertThat(auditCaptor.getValue().getReason()).isEqualTo(TransitionReason.TTL_EXPIRED);
    assertThat(auditCaptor.getValue().getEventId()).isNull();
  }

  @Test
  void shouldKeepRepeatedExpiredReadsIdempotent() {
    TraceState expiredState =
        new TraceState(
            "trace-1",
            TraceStatus.TTL_EXPIRED_FOR_EVENT,
            "event-1",
            "payment-created",
            EventResult.SUCCESS,
            OCCURRED_AT,
            "payment-confirmed",
            OCCURRED_AT.plusSeconds(60),
            1,
            null,
            OCCURRED_AT.plusSeconds(61));
    TraceStateEntity stateEntity = TraceStateEntity.from(expiredState);
    when(traceStateRepository.lockByTraceId("trace-1")).thenReturn(Optional.of(stateEntity));

    TraceState result = service.getStatus("trace-1");

    assertThat(result).isEqualTo(expiredState);
    verify(traceStateRepository, never()).save(any(TraceStateEntity.class));
    verify(traceStatusAuditRepository, never()).save(any(TraceStatusAuditEntity.class));
  }

  @Test
  void shouldThrowTraceNotFound_WhenTraceDoesNotExist() {
    when(traceStateRepository.lockByTraceId("missing-trace")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getStatus("missing-trace"))
        .isInstanceOf(TraceNotFoundException.class)
        .hasMessage("Trace not found: missing-trace");
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
