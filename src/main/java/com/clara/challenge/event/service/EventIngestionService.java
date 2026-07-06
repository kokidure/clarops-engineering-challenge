package com.clara.challenge.event.service;

import com.clara.challenge.event.domain.DuplicateEventComparator;
import com.clara.challenge.event.domain.DuplicateEventComparison;
import com.clara.challenge.event.domain.EventConflictException;
import com.clara.challenge.event.domain.EventTransitionService;
import com.clara.challenge.event.domain.IncomingEvent;
import com.clara.challenge.event.domain.TraceState;
import com.clara.challenge.event.domain.TraceStatus;
import com.clara.challenge.event.domain.TransitionResult;
import com.clara.challenge.event.persistence.EventEntity;
import com.clara.challenge.event.persistence.EventRepository;
import com.clara.challenge.event.persistence.TraceStateEntity;
import com.clara.challenge.event.persistence.TraceStateRepository;
import com.clara.challenge.event.persistence.TraceStatusAuditEntity;
import com.clara.challenge.event.persistence.TraceStatusAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

  private final EventRepository eventRepository;
  private final TraceStateRepository traceStateRepository;
  private final TraceStatusAuditRepository traceStatusAuditRepository;
  private final TraceStatusService traceStatusService;
  private final PlatformTransactionManager transactionManager;

  private final Clock clock;
  private final EventTransitionService transitionService = new EventTransitionService();
  private final DuplicateEventComparator duplicateComparator = new DuplicateEventComparator();

  public EventIngestionResult ingest(IncomingEvent event) {
    Objects.requireNonNull(event, "event is required");

    try {
      return inTransaction(
          () ->
              eventRepository
                  .findByEventId(event.eventId())
                  .map(existingEvent -> handleDuplicateEvent(existingEvent, event))
                  .orElseGet(() -> ingestNewEvent(event)));
    } catch (DataIntegrityViolationException exception) {
      return inTransaction(() -> handleConcurrentDuplicateEvent(event, exception));
    }
  }

  private EventIngestionResult handleDuplicateEvent(
      EventEntity existingEvent, IncomingEvent incomingEvent) {
    DuplicateEventComparison comparison =
        duplicateComparator.compare(existingEvent.toDomain(), incomingEvent);

    if (comparison == DuplicateEventComparison.CONFLICTING_DUPLICATE) {
      throw new EventConflictException("Duplicate eventId has different payload");
    }

    TraceStateEntity stateEntity =
        traceStateRepository
            .lockByTraceId(existingEvent.getTraceId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Trace state missing for accepted event " + existingEvent.getEventId()));

    TraceState state = traceStatusService.expireIfNeeded(stateEntity);
    return new EventIngestionResult(state, true);
  }

  private EventIngestionResult ingestNewEvent(IncomingEvent event) {
    EventEntity eventEntity = EventEntity.from(event, Instant.now(clock));
    TraceStateEntity currentStateEntity =
        traceStateRepository.lockByTraceId(event.traceId()).orElse(null);
    TraceState currentState = currentStateEntity == null ? null : currentStateEntity.toDomain();

    TransitionResult transitionResult =
        currentState == null
            ? transitionService.applyFirstEvent(event)
            : transitionService.applyNextEvent(currentState, event);

    eventRepository.saveAndFlush(eventEntity);

    saveTraceState(currentStateEntity, transitionResult.traceState());
    saveAudit(
        currentState == null ? null : currentState.status(), transitionResult, event.eventId());

    return new EventIngestionResult(transitionResult.traceState(), false);
  }

  private EventIngestionResult handleConcurrentDuplicateEvent(
      IncomingEvent event, DataIntegrityViolationException exception) {
    return eventRepository
        .findByEventId(event.eventId())
        .map(existingEvent -> handleDuplicateEvent(existingEvent, event))
        .orElseThrow(() -> exception);
  }

  private EventIngestionResult inTransaction(Supplier<EventIngestionResult> action) {
    return Objects.requireNonNull(
        new TransactionTemplate(transactionManager).execute(status -> action.get()));
  }

  private void saveTraceState(TraceStateEntity currentStateEntity, TraceState nextState) {
    if (currentStateEntity == null) {
      traceStateRepository.save(TraceStateEntity.from(nextState));
      return;
    }

    currentStateEntity.apply(nextState);
    traceStateRepository.save(currentStateEntity);
  }

  private void saveAudit(
      TraceStatus previousStatus, TransitionResult transitionResult, String eventId) {
    traceStatusAuditRepository.save(
        TraceStatusAuditEntity.transition(
            transitionResult.traceState().traceId(),
            previousStatus,
            transitionResult.traceState().status(),
            transitionResult.reason(),
            eventId));
  }
}
