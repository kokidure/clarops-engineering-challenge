# Distributed Event Watchdog

Spring Boot implementation of the Clarops Distributed Event Watchdog challenge.

The service receives distributed events, tracks the state of a flow by `traceId`, and reports whether the flow is started, waiting for another event, completed, or expired because an expected event did not arrive within the configured TTL.

The original challenge statement is preserved in [CHALLENGE_INSTRUCTIONS.md](CHALLENGE_INSTRUCTIONS.md).

## Current Status

The project is being implemented in phases. This README is maintained as the final solution document and grows as each phase is completed. Completed phase notes remain in place so the reasoning and implementation history are not lost when later phases add more behavior.

Implemented so far:

- Phase 1: assumptions, MVP scope, technical decisions, and implementation plan.
- Phase 2: PostgreSQL DDL for event history, current trace state, and status audit trail.
- Phase 3: public API DTOs, validation annotations, and API enum values.
- Phase 4: domain transition rules, conflict exceptions, and duplicate event comparison.
- Phase 5: unit tests for the pure domain transition and duplicate-comparison rules.
- Phase 6: JPA persistence entities, repositories, and transactional event ingestion service.
- Phase 7: public event/status endpoints, HTTP error mapping, and lazy TTL expiration on status reads.
- Phase 8: Hurl end-to-end tests, final documentation, and final verification commands.

## Scope

The MVP will expose two public endpoints under the existing `/api` context path:

```http
POST /v1/events
GET /v1/traces/{traceId}/status
```

The solution will stay intentionally small, but it will include enough production-aware behavior to make the flow reliable and explainable:

- immutable event history;
- current trace state for efficient status lookup;
- audit trail for state transitions;
- idempotent duplicate handling;
- conflict responses for invalid flow transitions;
- lazy TTL expiration when trace status is queried.

## Assumptions

|                           Assumption                            |                                                                 Behavior                                                                 |                                                Rationale                                                 |                                                   Trade-off                                                   |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| TTL is calculated from `occurredAt`.                            | `nextExpectedBefore = occurredAt + nextEventTtlSeconds`.                                                                                 | The event timestamp represents when the upstream service completed the action.                           | If producers send delayed or incorrect timestamps, expiration can be earlier or later than receive time.      |
| TTL expiration is evaluated lazily.                             | Expiration is checked when `GET /v1/traces/{traceId}/status` is called, and when an equivalent duplicate event retry reads stored state. | The challenge explicitly does not require a scheduler or background job.                                 | Expired traces are only detected when they are queried or when an idempotent duplicate retry loads the trace. |
| Lazy expiration is persisted.                                   | When expiration is detected, `trace_state` moves to `TTL_EXPIRED_FOR_EVENT`, `expired_at` is populated, and an audit row is written.     | Persisting the result makes repeated reads consistent and creates an extension point for future alerts.  | A read endpoint can mutate state, so this must be documented and tested.                                      |
| Event history is immutable.                                     | Accepted events are stored in an `events` table and not updated.                                                                         | This supports auditability and debugging of distributed flows.                                           | Requires a separate `trace_state` table for current status lookup.                                            |
| Current state is stored separately.                             | `trace_state` stores one row per `traceId`.                                                                                              | Status reads should not need to recompute the full event stream.                                         | State transitions must keep `events` and `trace_state` consistent.                                            |
| Duplicate `eventId` is idempotent only for equivalent payloads. | If all relevant fields match, the request returns `200 OK` with current trace status.                                                    | Safe retries should not create duplicate effects.                                                        | The implementation must compare fields explicitly.                                                            |
| Duplicate `eventId` with different payload is a conflict.       | The request returns `409 CONFLICT`.                                                                                                      | Same ID with different content is ambiguous and unsafe to accept.                                        | Clients must correct the event instead of relying on overwrite behavior.                                      |
| Unexpected event while waiting is rejected.                     | If a trace waits for `nextExpectedEvent`, any different event returns `409 CONFLICT` and does not mutate state.                          | The service models a strict expected-event flow.                                                         | More flexible branching workflows are out of scope.                                                           |
| Expected event after TTL is rejected.                           | The request returns `409 CONFLICT` and the trace remains `TTL_EXPIRED_FOR_EVENT`.                                                        | Once the SLA window expires, the flow should remain visibly expired.                                     | Late recovery would need an explicit remediation model, which is out of scope.                                |
| Completed traces are terminal.                                  | New events for a completed trace return `409 CONFLICT`.                                                                                  | A final event marks the flow as closed.                                                                  | Reopening flows is not supported in this MVP.                                                                 |
| `ERROR` is an event result, not a trace status.                 | Events with `result = ERROR` can still define a next expected event.                                                                     | The challenge only defines four trace statuses and allows event result to describe the upstream outcome. | Business-specific failure semantics are not modeled as separate trace statuses.                               |
| Metadata is stored as JSONB.                                    | The service stores `metadata` in PostgreSQL `JSONB`.                                                                                     | Metadata is flexible and not part of core transition rules.                                              | Querying metadata is out of scope for the MVP.                                                                |
| Unknown traces return `404 NOT FOUND`.                          | `GET /v1/traces/{traceId}/status` returns 404 when no trace exists.                                                                      | Missing data is different from an invalid flow transition.                                               | Clients must distinguish not found from conflict responses.                                                   |
| `trace_status_audit` records state transitions.                 | Important changes are appended with previous status, new status, reason, and event ID when available.                                    | This supports debugging and future notification/alerting integrations.                                   | It adds write overhead and another table to maintain.                                                         |

## Technical Decisions

|       Topic       |                                    Decision                                    |
|-------------------|--------------------------------------------------------------------------------|
| Application stack | Spring Boot 4, Java 21, Maven, PostgreSQL                                      |
| API style         | REST JSON API                                                                  |
| Persistence       | JPA repositories backed by PostgreSQL tables                                   |
| Schema management | Extend the existing Docker init SQL; no Flyway or Liquibase                    |
| Status model      | `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, `COMPLETED`         |
| Error format      | Standard JSON error response with code, message, optional traceId, and details |
| Validation        | Bean Validation annotations on request DTOs                                    |
| Testing focus     | Unit tests for business rules and Hurl tests for public HTTP behavior          |

## Implementation Progress

### Phase 1: Assumptions and Planning

Phase 1 established the product scope, business assumptions, implementation trade-offs, and the task breakdown for the rest of the challenge.

Delivered in this phase:

- Preserved the original challenge statement in [CHALLENGE_INSTRUCTIONS.md](CHALLENGE_INSTRUCTIONS.md).
- Defined the MVP endpoints and expected behavior.
- Documented assumptions around TTL calculation, lazy expiration, idempotency, conflicts, terminal statuses, and unknown traces.
- Chose the initial stack and architecture direction: Spring Boot 4, Java 21, Maven, PostgreSQL, JPA repositories, and REST JSON APIs.
- Created the implementation checklist in [TASKS.md](TASKS.md).

### Phase 2: Database DDL

Phase 2 added the database structure required by the planned event ingestion and trace status workflows. The DDL is intentionally ahead of the application code so later phases can map persistence and business behavior to an explicit schema.

Delivered in this phase:

- Added an immutable `events` table for accepted distributed events.
- Added a `trace_state` table for efficient current-status lookup by `traceId`.
- Added a `trace_status_audit` table for state transition history.
- Added constraints for valid statuses, event results, final-event rules, waiting-trace requirements, terminal timestamps, and idempotent event IDs.
- Added indexes for trace event lookup, status lookup, pending expiration lookup, and audit lookup.

### Phase 3: API DTOs

Phase 3 defined the public JSON contract that later endpoint and service phases will use. The DTOs live under `com.clara.challenge.event.api` and intentionally do not contain persistence or transition logic.

Delivered in this phase:

- Added `EventRequest` for `POST /v1/events` request payloads.
- Added `TraceStatusResponse` for `GET /v1/traces/{traceId}/status` responses.
- Added `ErrorResponse` for standard JSON error responses.
- Added API enums for event results, trace statuses, and error codes.
- Added Bean Validation rules for required fields, max string sizes, positive TTL values, paired `nextExpectedEvent`/`nextEventTtlSeconds`, and invalid `finalEvent` combinations.

### Phase 4: Domain Logic

Phase 4 added framework-free domain logic under `com.clara.challenge.event.domain`. The domain layer is intentionally independent from controllers and persistence so later phases can map API requests and database rows into the same transition rules.

Delivered in this phase:

- Added domain event result and trace status enums with the same vocabulary documented by the challenge.
- Added immutable domain records for incoming events, current trace state, and transition results.
- Added `EventTransitionService` for first-event and next-event state transitions.
- Added transition reasons aligned with the `trace_status_audit.reason` database constraint.
- Added domain exceptions for event conflicts and missing traces.
- Added `DuplicateEventComparator` for explicit duplicate `eventId` payload comparison.
- Kept business rules outside controllers, repositories, and entities.

Domain transition behavior:

- First event with `finalEvent = true` moves the trace to `COMPLETED`.
- First event with `nextExpectedEvent` and `nextEventTtlSeconds` moves the trace to `WAITING_OTHER_EVENT`.
- First event without a next expected event and not final moves the trace to `STARTED`.
- A waiting trace only accepts the exact expected event name.
- Expected events are accepted when `occurredAt` is on or before `nextExpectedBefore`; events after that deadline are rejected as conflicts.
- Waiting traces can only be expired after `nextExpectedBefore`; attempts to expire at or before the deadline are rejected.
- Transition reasons use the persisted audit vocabulary: `TRACE_CREATED`, `NEXT_EVENT_EXPECTED`, `EXPECTED_EVENT_RECEIVED`, `FINAL_EVENT_RECEIVED`, `TTL_EXPIRED`, and `DUPLICATE_EVENT_IDEMPOTENT`.
- Completed and expired traces reject new events.
- `SUCCESS` and `ERROR` are stored as event results; they do not directly determine trace status.
- Equivalent duplicate events are identified by comparing all relevant event fields, including metadata. Metadata is copied defensively while preserving JSON `null` values.

### Phase 5: Domain Unit Tests

Phase 5 added JUnit 5 tests for the framework-free domain layer before introducing persistence or HTTP endpoint behavior. The tests intentionally avoid a Spring context and use fixed timestamps so the core business rules can be verified without database or web concerns.

Delivered in this phase:

- Added `EventTransitionServiceTest` for first-event transitions, waiting traces, final events, TTL expiration, premature expiration rejection, expected/late/unexpected events, and terminal trace rejection.
- Added assertions that emitted transition reasons and the full `TransitionReason` enum match the audit schema vocabulary.
- Added coverage for preserving metadata entries with JSON `null` values while keeping metadata immutable.
- Added `DuplicateEventComparatorTest` for equivalent duplicates, conflicting duplicates, and different event IDs.
- Marked the Phase 5 checklist complete in [TASKS.md](TASKS.md).

### Phase 6: Persistence and Transactional Service

Phase 6 connected the domain layer to the PostgreSQL schema through JPA entities, repositories, and a transactional event ingestion service. This phase exposed the persistence flow as an application service for the Phase 7 controllers.

Delivered in this phase:

- Added `EventEntity`, `TraceStateEntity`, and `TraceStatusAuditEntity` mapped to the existing `events`, `trace_state`, and `trace_status_audit` tables.
- Added repositories for event lookup by `eventId`, trace-state lookup/update, and audit persistence.
- Added a pessimistic write lock for loading existing trace state during event ingestion.
- Added `EventIngestionService` to run event ingestion in one transaction: detect duplicate `eventId`, persist immutable event history, apply domain transition rules, update current trace state, and write an audit row.
- Flushes new event inserts before state mutation and reloads duplicate rows on unique insert conflicts so concurrent retries take the documented duplicate path instead of surfacing persistence errors.
- Added explicit mapping between persistence entities and framework-free domain records.
- Added `EventIngestionResult` so later endpoints can distinguish newly accepted events from idempotent duplicates.
- Added unit tests for the service orchestration paths using mocked repositories, without requiring a real database.

### Phase 7: Endpoints and Lazy Expiration

Phase 7 exposes the public Event Watchdog API through Spring MVC controllers and adds lazy expiration when a waiting trace status is read.

Delivered in this phase:

- Added `POST /v1/events`, returning `201 Created` for newly accepted events and `200 OK` for idempotent duplicate events.
- Added `GET /v1/traces/{traceId}/status`, delegating to `TraceStatusService` for status reads.
- Added global exception handling for validation errors, malformed JSON, unknown traces, event conflicts, and unexpected errors.
- Added `TraceStatusService` lazy expiration: waiting traces whose `nextExpectedBefore` is before the current clock are persisted as `TTL_EXPIRED_FOR_EVENT`, `expired_at` is populated, and a `TTL_EXPIRED` audit row is written.
- Kept repeated expired status reads idempotent by mutating only traces still in `WAITING_OTHER_EVENT`.
- Added unit tests for controller status/error mapping and lazy expiration service behavior.

### Phase 8: End-to-End Tests and Final Verification

Phase 8 validates the public HTTP API through Hurl scenarios and finalizes the documentation needed to run and review the solution.

Delivered in this phase:

- Added Hurl scenarios under `hurl/` for started, waiting, expected-event, completed, final error, TTL-expired, unexpected-event conflict, duplicate-idempotent, duplicate-conflict, duplicate-triggered lazy expiration, late-event conflict, and unknown-trace flows.
- Documented final API examples using the full local URLs exposed by the `/api` context path and `/v1` API version.
- Documented final data model and verification commands.
- Finalized the Phase 8 checklist in [TASKS.md](TASKS.md) and AI collaboration notes in [AI_USAGE.md](AI_USAGE.md).

## API Contract

The application uses `server.servlet.context-path=/api` and versioned controller mappings under `/v1`, so the full local endpoint URLs are:

```http
POST http://localhost:8081/api/v1/events
GET http://localhost:8081/api/v1/traces/{traceId}/status
```

### Event Request

`POST /v1/events` will accept this request body:

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "finalEvent": false,
  "metadata": {
    "country": "MX",
    "entityId": "company-123"
  }
}
```

Validation rules:

- `eventId`, `traceId`, `eventName`, `result`, and `occurredAt` are required.
- `eventId`, `traceId`, `eventName`, and `nextExpectedEvent` are limited to 120 characters to match the DDL.
- `result` must be `SUCCESS` or `ERROR`.
- `nextExpectedEvent` must not be blank when present.
- `nextEventTtlSeconds` must be positive when present.
- `nextExpectedEvent` and `nextEventTtlSeconds` must be provided together.
- `finalEvent` defaults to `false` and cannot be combined with `nextExpectedEvent` or `nextEventTtlSeconds`.

### Trace Status Response

`GET /v1/traces/{traceId}/status` will return the current trace status in this shape:

```json
{
  "traceId": "trace-123",
  "status": "WAITING_OTHER_EVENT",
  "lastEventName": "APPLICATION_RECEIVED",
  "lastEventResult": "SUCCESS",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextExpectedBefore": "2026-06-15T10:02:00Z",
  "eventsReceived": 1
}
```

Supported trace statuses are `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, and `COMPLETED`.

### Error Response

Error responses will use a standard JSON shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "traceId": "trace-123",
  "details": {
    "eventId": "must not be blank"
  },
  "timestamp": "2026-06-15T10:00:00Z"
}
```

Supported error codes are `VALIDATION_ERROR`, `TRACE_NOT_FOUND`, `EVENT_CONFLICT`, and `INTERNAL_ERROR`.

### Final API Examples

Create a trace that starts and waits for a next event:

```bash
curl -s -X POST http://localhost:8081/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "traceId": "trace-123",
    "eventName": "APPLICATION_RECEIVED",
    "result": "SUCCESS",
    "occurredAt": "2026-06-15T10:00:00Z",
    "nextExpectedEvent": "RULES_EVALUATED",
    "nextEventTtlSeconds": 120,
    "metadata": {
      "country": "MX",
      "entityId": "company-123"
    }
  }'
```

Expected response: `201 Created` with `status: "WAITING_OTHER_EVENT"` and `nextExpectedBefore` equal to `occurredAt + nextEventTtlSeconds`.

Read current trace status:

```bash
curl -s http://localhost:8081/api/v1/traces/trace-123/status
```

Expected response: `200 OK` with the current trace state. If the waiting trace's `nextExpectedBefore` is already in the past, this read lazily persists `TTL_EXPIRED_FOR_EVENT`.

Retry the same event payload:

```bash
curl -s -X POST http://localhost:8081/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "traceId": "trace-123",
    "eventName": "APPLICATION_RECEIVED",
    "result": "SUCCESS",
    "occurredAt": "2026-06-15T10:00:00Z",
    "nextExpectedEvent": "RULES_EVALUATED",
    "nextEventTtlSeconds": 120,
    "metadata": {
      "country": "MX",
      "entityId": "company-123"
    }
  }'
```

Expected response: `200 OK`, because equivalent duplicate `eventId` payloads are idempotent.

Equivalent duplicate retries return the current trace state. If that stored trace is waiting and already past its TTL, the retry can also trigger the same lazy expiration path used by the status endpoint.

## Data Model

The PostgreSQL DDL is defined in `docker/init-scripts/db/01-init-schema.sql`. The schema uses one
immutable history table, one current-state table, and one append-only audit table.

|        Table         |                                             Purpose                                              |                                                                                  Key columns                                                                                  |
|----------------------|--------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `events`             | Stores accepted distributed events as immutable history.                                         | `id`, `event_id`, `trace_id`, `event_name`, `result`, `occurred_at`, `received_at`, `next_expected_event`, `next_event_ttl_seconds`, `metadata`                               |
| `trace_state`        | Stores the current status of each trace for efficient `GET /v1/traces/{traceId}/status` lookups. | `trace_id`, `status`, `last_event_id`, `last_event_name`, `last_event_result`, `next_expected_event`, `next_expected_before`, `events_received`, `completed_at`, `expired_at` |
| `trace_status_audit` | Records state transitions and supports future alerting or debugging use cases.                   | `id`, `trace_id`, `previous_status`, `new_status`, `reason`, `event_id`, `created_at`                                                                                         |

Important constraints and indexes:

- `events.event_id` is unique to support idempotency checks.
- `events.result` is restricted to `SUCCESS` or `ERROR`.
- `events.next_expected_event` and `events.next_event_ttl_seconds` must be provided together.
- `events.final_event` cannot be combined with `next_expected_event` or `next_event_ttl_seconds`.
- `trace_state.status` is restricted to `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, or `COMPLETED`.
- `trace_state.events_received` defaults to `1` and must stay greater than `0` because a trace state exists only after the first accepted event.
- `trace_state` requires waiting traces to have both `next_expected_event` and `next_expected_before`.
- `trace_state` requires terminal timestamps for completed and expired traces.
- Indexes support event lookup by trace, status lookup, pending-expiration lookup, and audit lookup by trace or creation time.

## Initial Task Breakdown

1. Document assumptions, decisions, and trade-offs in this README.
2. Define PostgreSQL DDL for event history, trace state, and audit trail.
3. Define API request, response, and error DTOs.
4. Implement state transition logic outside controllers.
5. Add unit tests for domain transition rules before adding infrastructure.
6. Implement persistence entities, repositories, and transactional service flow.
7. Expose `POST /v1/events` and `GET /v1/traces/{traceId}/status`, including lazy TTL expiration.
8. Add Hurl end-to-end tests, complete final documentation, and run final verification.

## Running the Project

For local setup and run instructions, see [SETUP.md](SETUP.md).

### Final Verification Commands

Run unit and application-context tests plus formatting checks:

```bash
./mvnw clean verify
```

Start the application with Docker/PostgreSQL:

```bash
cp docker/example.env docker/.env
./mvnw spring-boot:run
```

The Hurl scenarios use fixed `phase8-*` event IDs, so run them against a clean database. To reset local test data:

```bash
PGPASSWORD=clarops_pass psql -h localhost -p 5433 -U clarops_user -d clarops_challenge <<'SQL'
TRUNCATE
  clarops_challenge_schema.trace_status_audit,
  clarops_challenge_schema.trace_state,
  clarops_challenge_schema.events;
SQL
```

If `psql` is not available locally, run the truncation through the Docker container:

```bash
docker compose -f docker/docker-compose.yml exec -T \
  -e PGPASSWORD=clarops_pass \
  challenge-postgresql \
  psql -U clarops_user -d clarops_challenge \
  -c "TRUNCATE clarops_challenge_schema.trace_status_audit, clarops_challenge_schema.trace_state, clarops_challenge_schema.events;"
```

With the app running, execute the public HTTP E2E tests:

```bash
hurl --test hurl/*.hurl
```

The test suite covers 12 scenarios across the following files:

|                 File                  |                    Scenario                     |
|---------------------------------------|-------------------------------------------------|
| `hurl/started-flow.hurl`              | Trace starts and reaches `STARTED`              |
| `hurl/waiting-other-event-flow.hurl`  | Trace moves to `WAITING_OTHER_EVENT`            |
| `hurl/expected-event-flow.hurl`       | Expected event arrives and advances the trace   |
| `hurl/completed-flow.hurl`            | Final event completes the trace                 |
| `hurl/error-final-event-flow.hurl`    | Final event with `result: ERROR`                |
| `hurl/ttl-expired-flow.hurl`          | TTL expires on status read                      |
| `hurl/unexpected-event-conflict.hurl` | Unexpected event rejected with 409              |
| `hurl/duplicate-idempotent-flow.hurl` | Equivalent duplicate returns 200                |
| `hurl/duplicate-conflict.hurl`        | Different duplicate rejected with 409           |
| `hurl/duplicate-lazy-expiration.hurl` | Duplicate retry triggers lazy expiration        |
| `hurl/late-event-conflict.hurl`       | Expected event after deadline rejected with 409 |
| `hurl/unknown-trace.hurl`             | Unknown trace returns 404                       |

At the beginning of Phase 1, the repository still contains only the baseline health endpoint:

```http
GET /api/v1/health
```

