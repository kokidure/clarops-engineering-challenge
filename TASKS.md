# Implementation Tasks

This task list tracks the implementation phases for the challenge solution.

## Phase 1 - Define Assumptions

- [x] Preserve the original challenge statement as `CHALLENGE_INSTRUCTIONS.md`.
- [x] Create a solution-focused `README.md`.
- [x] Document MVP scope.
- [x] Document assumptions, behavior, rationale, and trade-offs.
- [x] Document initial technical decisions.
- [x] Document the implementation task breakdown.

## Phase 2 - Define DDL

- [x] Extend `docker/init-scripts/db/01-init-schema.sql`.
- [x] Add the immutable `events` table.
- [x] Add the `trace_state` table.
- [x] Add the `trace_status_audit` table.
- [x] Add constraints and indexes for idempotency and lookup paths.

## Phase 3 - Define API DTOs

- [x] Add `EventRequest`.
- [x] Add `TraceStatusResponse`.
- [x] Add `ErrorResponse`.
- [x] Add validation annotations.
- [x] Define enum values used by the API.

## Phase 4 - Implement Domain Logic

- [x] Add trace status transition rules.
- [x] Add event result handling.
- [x] Add domain exceptions for conflicts and missing traces.
- [x] Add explicit duplicate payload comparison.
- [x] Keep business rules outside controllers.

## Phase 5 - Add Domain Unit Tests

- [x] Test first event creates `STARTED`.
- [x] Test first event with next expected event creates `WAITING_OTHER_EVENT`.
- [x] Test final event creates `COMPLETED`.
- [x] Test TTL expiration creates `TTL_EXPIRED_FOR_EVENT`.
- [x] Test premature TTL expiration is rejected.
- [x] Test expected event before TTL advances state.
- [x] Test expected event after TTL is rejected.
- [x] Test unexpected event is rejected.
- [x] Test equivalent duplicate event is idempotent.
- [x] Test different duplicate event returns conflict.
- [x] Test completed trace rejects new events.
- [x] Test expired trace rejects new events.
- [x] Test metadata with JSON null values is preserved.
- [x] Test transition reasons match the audit schema vocabulary.

## Phase 6 - Implement Persistence and Transactional Service

- [x] Add event entity and repository.
- [x] Add trace state entity and repository.
- [x] Add trace status audit entity and repository.
- [x] Map persistence rows to domain records.
- [x] Implement transactional event ingestion.
- [x] Use domain transition rules and duplicate comparison.
- [x] Keep event history, current state, and audit rows consistent.
- [x] Add unit tests for transactional event ingestion.

## Phase 7 - Implement Endpoints and Lazy Expiration

- [x] Add `POST /events`.
- [x] Add `GET /traces/{traceId}/status`.
- [x] Add global exception handling.
- [x] Return standard error responses.
- [x] Return correct HTTP status codes.
- [x] Detect expired waiting traces on status reads.
- [x] Persist `TTL_EXPIRED_FOR_EVENT`.
- [x] Populate `expired_at`.
- [x] Write `TTL_EXPIRED` audit rows.
- [x] Keep repeated status reads idempotent.

## Phase 8 - Add Hurl E2E Tests, Final Docs, and Verification

- [x] Add started flow scenario.
- [x] Add waiting-other-event flow scenario.
- [x] Add expected-event flow scenario.
- [x] Add completed flow scenario.
- [x] Add final-error flow scenario.
- [x] Add TTL-expired flow scenario.
- [x] Add unexpected-event conflict scenario.
- [x] Add duplicate-idempotent flow scenario.
- [x] Add duplicate-conflict scenario.
- [x] Add duplicate-triggered lazy expiration scenario.
- [x] Add late-event conflict scenario.
- [x] Add unknown-trace scenario.
- [x] Update README with final API examples.
- [x] Update README with final data model.
- [x] Update README with final test commands.
- [x] Add `AI_USAGE.md`.
- [x] Finalize accepted and rejected AI suggestions.
- [x] Run Maven verification.
- [x] Run formatting checks.
- [x] Start the app with Docker/PostgreSQL.
- [x] Run Hurl tests.
- [x] Do a final README review.

