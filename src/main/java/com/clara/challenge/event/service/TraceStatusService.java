package com.clara.challenge.event.service;

import com.clara.challenge.event.domain.EventTransitionService;
import com.clara.challenge.event.domain.TraceNotFoundException;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.domain.TraceStatus;
import com.clara.challenge.event.domain.TransitionResult;
import com.clara.challenge.event.persistence.TraceStateEntity;
import com.clara.challenge.event.persistence.TraceStateRepository;
import com.clara.challenge.event.persistence.TraceStatusAuditEntity;
import com.clara.challenge.event.persistence.TraceStatusAuditRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraceStatusService {

  private final TraceStateRepository traceStateRepository;
  private final TraceStatusAuditRepository traceStatusAuditRepository;
  private final Clock clock;

  private final EventTransitionService transitionService = new EventTransitionService();

  @Transactional
  public TraceState getStatus(String traceId) {
    TraceStateEntity stateEntity =
        traceStateRepository
            .lockByTraceId(traceId)
            .orElseThrow(() -> new TraceNotFoundException(traceId));

    return expireIfNeeded(stateEntity);
  }

  TraceState expireIfNeeded(TraceStateEntity stateEntity) {
    TraceState currentState = stateEntity.toDomain();
    if (!shouldExpire(currentState)) {
      return currentState;
    }

    TransitionResult transitionResult =
        transitionService.expireWaitingTrace(currentState, Instant.now(clock));
    stateEntity.apply(transitionResult.traceState());
    traceStateRepository.save(stateEntity);
    traceStatusAuditRepository.save(
        TraceStatusAuditEntity.transition(
            transitionResult.traceState().traceId(),
            currentState.status(),
            transitionResult.traceState().status(),
            transitionResult.reason(),
            null));

    return transitionResult.traceState();
  }

  private boolean shouldExpire(TraceState state) {
    return state.status() == TraceStatus.WAITING_OTHER_EVENT
        && Instant.now(clock).isAfter(state.nextExpectedBefore());
  }
}
