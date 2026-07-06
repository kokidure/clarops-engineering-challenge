package com.clara.challenge.event.api;

import com.clara.challenge.event.domain.EventResult;
import com.clara.challenge.event.domain.TraceStatus;
import java.time.Instant;

public record TraceStatusResponse(
    String traceId,
    TraceStatus status,
    String lastEventName,
    EventResult lastEventResult,
    String nextExpectedEvent,
    Instant nextExpectedBefore,
    int eventsReceived) {}
