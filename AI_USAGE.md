# AI Usage

This document records how AI assistance was used during the challenge implementation.

The file is updated incrementally at the end of each implementation phase so the final submission shows which suggestions were accepted, rejected, or manually adjusted.

## Tools Used

|              Tool              |                                                 Usage                                                  |
|--------------------------------|--------------------------------------------------------------------------------------------------------|
| OpenCode                       | Repository exploration, implementation assistance, documentation drafting, and verification commands.  |
| Engram                         | Session memory for preserving planning decisions and implementation context across work sessions.      |
| PR description writer subagent | Drafting, creating, and editing GitHub PR descriptions from the repository PR template.                |
| OpenCode `/review` command     | Built-in review command for branch and diff inspection before accepting or documenting changes.        |
| Codex cloud code reviewer      | Automated GitHub PR review comments used as an additional correctness and regression signal.           |
| ChatGPT                        | Prompt refinement and optimization before using AI-assisted implementation or documentation workflows. |

## Models Used

|       Model       |                          Role                           |
|-------------------|---------------------------------------------------------|
| GPT-5.5           | Main coding agent used through OpenCode.                |
| GPT-5.4 Mini Fast | PR description writer subagent used for GitHub PR body. |

## Phase 1 - Assumptions and Documentation

### Prompts and Requests

- Asked the assistant to analyze the repository and explain the challenge.
- Asked for a manual implementation plan before writing code.
- Asked to normalize implementation phases and prompts.
- Asked to start Phase 1 by preserving the original challenge instructions and creating a new solution README.

### Accepted Suggestions

- Preserve the original challenge statement as `CHALLENGE_INSTRUCTIONS.md`.
- Create a solution-focused `README.md` with assumptions, trade-offs, technical decisions, and task breakdown.
- Track implementation work with a phase-based `TASKS.md`.

### Rejected or Adjusted Suggestions

- Internal planning notes were kept outside the final challenge documentation.

### Manual Corrections

- Pull requests must be created explicitly inside the fork repository, not against the original upstream repository.

## Phase 2 - Database DDL

### Prompts and Requests

Main DDL prompt:

> Start Phase 2 and implement the PostgreSQL DDL for the Event Watchdog MVP.
>
> Use the challenge requirements and the decisions already documented in `README.md`:
>
> - keep immutable event history;
> - keep current trace state for efficient status lookup;
> - add an audit trail for state transitions;
> - support idempotency checks with a unique `eventId`;
> - support strict statuses: `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, and `COMPLETED`;
> - support event results `SUCCESS` and `ERROR`;
> - calculate TTL from `occurredAt` plus `nextEventTtlSeconds`;
> - persist lazy TTL expiration;
> - store flexible event metadata as PostgreSQL `JSONB`;
> - add constraints and indexes that make these assumptions explicit;
> - extend the existing Docker init SQL instead of adding Flyway or Liquibase.
>
> Use this table structure as the target design:
>
> - `events`: `id UUID PRIMARY KEY`, `event_id VARCHAR(120) UNIQUE NOT NULL`, `trace_id VARCHAR(120) NOT NULL`, `event_name VARCHAR(120) NOT NULL`, `result VARCHAR(20) NOT NULL`, `occurred_at TIMESTAMPTZ NOT NULL`, `received_at TIMESTAMPTZ NOT NULL`, `next_expected_event VARCHAR(120)`, `next_event_ttl_seconds INTEGER`, `final_event BOOLEAN NOT NULL DEFAULT FALSE`, `metadata JSONB`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`;
> - `trace_state`: `trace_id VARCHAR(120) PRIMARY KEY`, `status VARCHAR(40) NOT NULL`, `last_event_id VARCHAR(120) NOT NULL`, `last_event_name VARCHAR(120) NOT NULL`, `last_event_result VARCHAR(20) NOT NULL`, `last_event_occurred_at TIMESTAMPTZ NOT NULL`, `next_expected_event VARCHAR(120)`, `next_expected_before TIMESTAMPTZ`, `events_received INTEGER NOT NULL DEFAULT 1`, `completed_at TIMESTAMPTZ`, `expired_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`;
> - `trace_status_audit`: `id UUID PRIMARY KEY`, `trace_id VARCHAR(120) NOT NULL`, `previous_status VARCHAR(40)`, `new_status VARCHAR(40) NOT NULL`, `reason VARCHAR(80) NOT NULL`, `event_id VARCHAR(120)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
>
> Add these indexes for expected lookup paths:
>
> - `events(trace_id)` for event history lookup by trace;
> - `events(trace_id, occurred_at)` for ordered event history inspection;
> - `trace_state(status)` for status-based lookups;
> - `trace_state(next_expected_before)` for pending TTL expiration lookup;
> - `trace_status_audit(trace_id)` for audit lookup by trace;
> - `trace_status_audit(created_at)` for chronological audit inspection.
>
> Add a constraint so `final_event = true` cannot be combined with `next_expected_event` or `next_event_ttl_seconds`, because a terminal event must not also declare another expected event.
>
> Keep the implementation minimal and explain any trade-offs or alternatives that should be documented.

Additional requests:

- "Should `AI_USAGE.md` be updated incrementally by phase so the final submission documents the AI usage clearly?"
- "Create the local Docker environment file and make sure the Compose volume name does not conflict with other projects."
- "Use local defaults that avoid port conflicts: Spring Boot on `8081` and PostgreSQL exposed on `5433`."
- "Help me configure the local machine to use Java 21 with asdf instead of the Java version installed by Homebrew."
- "Re-run `./mvnw spotless:check` now that Java 21 is configured, and document the Java 21 `.tool-versions` setup."
- "Verify whether everything is in order before committing the Phase 2 changes."

### Accepted Suggestions

- Create `AI_USAGE.md` early and update it incrementally after each phase.
- Extend the existing Docker init SQL instead of adding Flyway or Liquibase.
- Add an immutable `events` table for event history.
- Add a `trace_state` table for efficient current status lookup.
- Add a `trace_status_audit` table as a state transition audit log and future alerting extension point.
- Add constraints for event result, trace status, positive TTL values, required waiting-state fields, terminal events, completed timestamps, and expired timestamps.
- Add indexes for event trace lookup, trace status lookup, pending expiration lookup, and audit lookup.

### Rejected or Adjusted Suggestions

- No separate migration framework was added because the challenge repository already uses Docker initialization SQL and explicitly does not require extra infrastructure.
- Payload hashing was not added to the DDL; explicit field comparison remains the MVP approach, with hashing left as a future improvement.

### Manual Corrections

- The local Java runtime was corrected to Java 21 with asdf before using `./mvnw spotless:check` as a validation signal.

## Phase 3 - API DTOs

### Prompts and Requests

Main DTO prompt:

> Start Phase 3 from the updated `develop` branch and implement the API DTO contract for the Event Watchdog MVP.
>
> Before writing code:
>
> - verify the working tree is clean;
> - run a fresh `git pull --ff-only` on `develop`;
> - create a new branch named `phase-3-api-dtos`;
> - review `CHALLENGE_INSTRUCTIONS.md`, `TASKS.md`, and the existing `README.md` so the DTOs match the challenge contract and previously documented assumptions.
>
> Keep the phase strictly scoped to DTOs and API contract types. Do not add controllers, services, repositories, entities, persistence mapping, state transition logic, or Hurl tests in this phase.
>
> Add public API contract types under `src/main/java/com/clara/challenge/event/api`:
>
> - `EventRequest` for `POST /events`;
> - `TraceStatusResponse` for `GET /traces/{traceId}/status`;
> - `ErrorResponse` for standard JSON errors;
> - enum values used by the API.
>
> `EventRequest` must support the request fields from the challenge instructions:
>
> - `eventId`;
> - `traceId`;
> - `eventName`;
> - `result`;
> - `occurredAt`;
> - `nextExpectedEvent`;
> - `nextEventTtlSeconds`;
> - `finalEvent`;
> - `metadata`.
>
> Use Java records for DTOs and Bean Validation annotations for the request contract:
>
> - required fields must be non-null/non-blank as appropriate;
> - string fields that map to the DDL should be limited to 120 characters;
> - `result` must use an enum with allowed values `SUCCESS` and `ERROR`;
> - `occurredAt` must be required and represented as an instant/date-time type suitable for ISO-8601 timestamps;
> - `nextEventTtlSeconds` must be positive when present;
> - `nextExpectedEvent` and `nextEventTtlSeconds` must be provided together;
> - `nextExpectedEvent` must not be blank when present;
> - `finalEvent` defaults to `false` when omitted;
> - `finalEvent = true` cannot be combined with `nextExpectedEvent` or `nextEventTtlSeconds`.
>
> `TraceStatusResponse` should include enough fields to explain the current trace state, aligned with the README and challenge example:
>
> - `traceId`;
> - `status`;
> - `lastEventName`;
> - `lastEventResult`;
> - `nextExpectedEvent`;
> - `nextExpectedBefore`;
> - `eventsReceived`.
>
> Define trace status enum values exactly as documented: `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, and `COMPLETED`.
>
> Define `ErrorResponse` with a stable structure that later exception handling can reuse, including an error code, message, optional `traceId`, details, and timestamp. Include error code enum values for validation errors, missing traces, event conflicts, and unexpected internal errors.
>
> Update documentation cumulatively:
>
> - mark Phase 3 complete in `TASKS.md`;
> - add Phase 3 progress to `README.md` without replacing Phase 1 or Phase 2 notes;
> - document the request, status response, error response, enum values, and validation rules in `README.md`;
> - update `AI_USAGE.md` with this Phase 3 prompt, accepted suggestions, rejected suggestions, and manual corrections.
>
> Run formatting and verification after the changes. Prefer `./mvnw spotless:apply test` or `./mvnw clean test` depending on what is needed. Report any warnings separately from failures.

Additional requests:

- "Explain Phase 3 from the updated `develop` branch before implementing it."

### Accepted Suggestions

- Keep Phase 3 scoped to API contracts only: DTOs, validation annotations, and enum values.
- Use Java records for request/response DTOs to keep the contract immutable and concise.
- Put public event API contracts under `com.clara.challenge.event.api` so later phases can add domain, persistence, and controller packages without mixing responsibilities.
- Use Bean Validation on `EventRequest` for required fields, size limits, positive TTL, paired next-event fields, and invalid final-event combinations.

### Rejected or Adjusted Suggestions

- No controllers, services, entities, repositories, or state transition logic were added in Phase 3 because those are assigned to later phases.

### Manual Corrections

- The README was updated cumulatively by adding Phase 3 progress and API contract sections instead of replacing earlier phase notes.

## Phase 4 - Domain Logic

### Prompts and Requests

Main domain prompt:

> Start Phase 4 from the updated `develop` branch and implement the domain logic for the Event Watchdog MVP.
>
> Before writing code:
>
> - verify the working tree is clean;
> - run a fresh `git pull --ff-only` on `develop`;
> - create a new branch named `phase-4-domain-logic`;
> - review `TASKS.md`, `README.md`, `CHALLENGE_INSTRUCTIONS.md`, and the Phase 3 API contracts.
>
> Keep this phase strictly scoped to domain logic. Do not add controllers, JPA entities, repositories, transactional persistence flows, Hurl tests, or global exception handlers.
>
> Implement the Phase 4 deliverables:
>
> - status enum;
> - result enum;
> - transition service;
> - domain exceptions;
> - duplicate payload comparison.
>
> Add domain code under `src/main/java/com/clara/challenge/event/domain` and keep it framework-free so it can be unit-tested without Spring.
>
> Implement transition rules aligned with the README assumptions and challenge behavior:
>
> - first event with `finalEvent = true` becomes `COMPLETED`;
> - first event with `nextExpectedEvent` and `nextEventTtlSeconds` becomes `WAITING_OTHER_EVENT`;
> - first event without a next expected event and not final becomes `STARTED`;
> - when a trace is waiting, only the exact `nextExpectedEvent` is accepted;
> - expected events after the TTL deadline are rejected;
> - completed traces reject new events;
> - expired traces reject new events;
> - event result values `SUCCESS` and `ERROR` are event outcomes, not trace statuses.
>
> Add explicit duplicate `eventId` comparison that treats a duplicate as idempotent only when all relevant fields match, including `traceId`, `eventName`, `result`, `occurredAt`, next expected fields, `finalEvent`, and `metadata`. Different payloads for the same `eventId` must be distinguishable as conflicts.
>
> Add domain exceptions that later endpoint and persistence phases can map to HTTP responses, including event conflicts and missing traces.
>
> Update documentation cumulatively:
>
> - mark Phase 4 complete in `TASKS.md`;
> - add Phase 4 progress and transition behavior to `README.md` without replacing prior phase notes;
> - update `AI_USAGE.md` with this full Phase 4 prompt, accepted suggestions, rejected suggestions, and manual corrections.
>
> Run formatting and verification after the changes. Report warnings separately from failures.

### Accepted Suggestions

- Keep the domain layer framework-free and independent from controllers/persistence.
- Use immutable records for incoming events, trace state, and transition results.
- Reuse the same status/result vocabulary as the public API while keeping domain types in a domain package for later mapping.
- Implement duplicate detection through explicit field-by-field comparison instead of a payload hash.

### Rejected or Adjusted Suggestions

- No Spring service annotations were added because this phase is pure domain logic.
- No persistence mutation or lazy-expiration storage was added; persistence and endpoint-triggered expiration are assigned to later phases.

### Manual Corrections

- The README was updated as an additive final-solution document by appending Phase 4 progress and behavior notes.
- A review found that `expireWaitingTrace` could expire a waiting trace before the TTL deadline; the domain service now rejects expiration at or before `nextExpectedBefore`.
- A review found that `Map.copyOf` rejected metadata entries with JSON `null` values; metadata is now defensively copied with an unmodifiable map that preserves null values.
- A PR review found that transition reasons did not match the audit schema constraint; `TransitionReason` now uses the persisted audit vocabulary directly.
- The remaining roadmap was reduced to 8 phases and domain unit tests were moved before persistence so the core rules are verified before adding database and HTTP layers.

## Phase 5 - Domain Unit Tests

### Prompt Used

```text
Add Phase 5 domain unit tests for the Event Watchdog service.

Use the following testing standard:
- Use JUnit 5 with AssertJ assertions.
- Test method names must follow shouldExpectedBehavior_WhenCondition.
- Use Arrange / Act / Assert structure.
- Each test should validate one business rule or invariant.
- Avoid Spring context because the tested classes are pure domain code.
- Use fixed Instants for time-dependent rules.
- Do not add tests for behavior outside the challenge requirements unless clearly tied to a documented assumption.
- Make the requirement or assumption validated by each test clear from the test name and assertions.

Cover EventTransitionService business rules, DuplicateEventComparator duplicate classification, metadata preservation with JSON null values, and TransitionReason values matching the audit schema vocabulary. Keep the tests focused, readable, and aligned with the existing domain API.
```

### Accepted Suggestions

- Added focused unit tests for `EventTransitionService` state transitions, terminal-state rejections, TTL expiration boundaries, metadata copying, and audit-compatible transition reasons.
- Added focused unit tests for `DuplicateEventComparator` covering equivalent duplicates, conflicting duplicates, and different event IDs.

### Rejected or Adjusted Suggestions

- No Spring context was used for Phase 5 tests because the tested logic is pure domain code.

## Phase 6 - Persistence and Transactional Service

### Prompt Used

```text
Start Phase 6 from the updated develop branch and implement the persistence layer plus transactional event ingestion service for the Event Watchdog MVP.

Before writing code:
- verify the working tree is clean;
- create a new branch named phase-6-persistence-service;
- review CHALLENGE_INSTRUCTIONS.md, README.md, TASKS.md, the Phase 2 DDL, Phase 4 domain code, and Phase 5 tests.

Keep this phase strictly scoped to persistence and service orchestration. Do not add REST controllers, endpoint exception mapping, lazy expiration on status reads, Hurl tests, or final documentation.

Implement:
- JPA entities for events, trace_state, and trace_status_audit, mapped to the existing PostgreSQL schema;
- repositories for event lookup by eventId, trace state lookup/update, and audit insertion;
- mapping between JPA entities and the framework-free domain records;
- a transactional event ingestion service that detects duplicate eventId, compares duplicate payloads explicitly, persists accepted event history, applies EventTransitionService rules, updates current trace state, and writes audit rows for accepted transitions;
- an ingestion result that later endpoints can use to distinguish new accepted events from idempotent duplicates.

Use the existing domain services instead of duplicating transition rules in persistence code. Keep duplicate retries idempotent by returning current trace state without inserting another event or mutating trace state. Protect concurrent retries of the same event by flushing event inserts before state mutation and reloading/comparing the duplicate row if the insert hits a unique constraint. Preserve JSON metadata as JSONB.

Add unit tests for the Phase 6 service orchestration using the same standard from Phase 5:
- Use JUnit 5, AssertJ, and Mockito.
- Test method names must follow shouldExpectedBehavior_WhenCondition.
- Use Arrange / Act / Assert structure.
- Each test should validate one service rule or persistence interaction.
- Avoid a real database; mock repositories and capture saved entities.
- Do not test public HTTP behavior because endpoints belong to Phase 7.

Cover:
- first accepted event saves event history, creates trace state, and writes an audit row;
- expected event on an existing waiting trace updates trace state and writes an audit row;
- equivalent duplicate event returns the current trace state without inserting or mutating;
- concurrent duplicate insert conflict reloads the existing event and returns the idempotent duplicate result;
- conflicting duplicate event raises EventConflictException and does not write state or audit rows.

Update TASKS.md, README.md, and AI_USAGE.md cumulatively for Phase 6, and run formatting plus tests.
```

### Accepted Suggestions

- Added JPA entities aligned with the existing DDL for immutable events, current trace state, and trace status audit.
- Added Spring Data repositories, including event lookup by `eventId` and a pessimistic write lock for existing trace state during ingestion.
- Added `EventIngestionService` as the transactional application boundary for event ingestion.
- Reused `EventTransitionService` and `DuplicateEventComparator` instead of duplicating business rules in the persistence layer.
- Added entity-to-domain mapping so persistence rows can be passed into the domain layer.
- Added mocked unit tests for new-event ingestion, expected-event ingestion, idempotent duplicate handling, concurrent duplicate insert handling, and conflicting duplicate handling.

### Rejected or Adjusted Suggestions

- No REST controllers or HTTP status mapping were added because endpoints are assigned to Phase 7.
- No lazy expiration on status reads was added because endpoint-triggered expiration belongs to Phase 7.
- Idempotent duplicate retries return the current trace state without writing another event row or mutating trace state.

## Phase 7 - Endpoints and Lazy Expiration

### Prompt Used

```text
Start Phase 7 from the updated develop branch and implement the public REST endpoints plus lazy TTL expiration for the Event Watchdog MVP.

Before writing code:
- verify the working tree and current branch;
- review CHALLENGE_INSTRUCTIONS.md, README.md, TASKS.md, plan.md, the existing API DTOs, domain logic, persistence entities, and Phase 6 service tests.

Implement:
- POST /events by delegating to EventIngestionService;
- GET /traces/{traceId}/status by delegating to a status service;
- global exception mapping for validation errors, malformed JSON, unknown traces, business conflicts, and unexpected failures;
- standard error responses with code, message, traceId, details, and timestamp;
- lazy expiration when status is queried for a WAITING_OTHER_EVENT trace whose nextExpectedBefore is before now;
- persistence of TTL_EXPIRED_FOR_EVENT, expiredAt, and a TTL_EXPIRED audit row;
- idempotent repeated status reads after expiration.

Testing standard:
- Use JUnit 5, AssertJ, Mockito, and MockMvc standalone tests where appropriate.
- Test method names must follow shouldExpectedBehavior_WhenCondition.
- Use Arrange / Act / Assert structure.
- Keep controller tests focused on HTTP status and response mapping.
- Keep lazy expiration tests at the service level with mocked repositories and a fixed Clock.
- Do not add Hurl tests in this phase because Hurl validation belongs to Phase 8.

Cover:
- new event returns 201;
- idempotent duplicate returns 200;
- status endpoint returns current trace state;
- validation errors return 400 with field details;
- malformed JSON returns 400;
- unknown traces return 404;
- business conflicts return 409;
- waiting trace past TTL persists expired state and audit row;
- non-expired or already expired status reads do not mutate state.

Update TASKS.md, README.md, and AI_USAGE.md cumulatively for Phase 7, and run formatting plus tests.
```

### Accepted Suggestions

- Added `EventController` for public event ingestion and trace status endpoints.
- Added `GlobalExceptionHandler` with standard API error responses and stable HTTP status mapping.
- Added `TraceStatusService` to lazily expire waiting traces on status reads using an injectable `Clock` for tests.
- Reused existing domain transition logic for TTL expiration instead of duplicating status rules in the controller.
- Added mocked unit tests for controller response mapping and lazy expiration persistence/audit behavior.

### Rejected or Adjusted Suggestions

- No Hurl end-to-end tests were added because they are assigned to Phase 8.
- Controller methods remain thin; persistence and transition decisions stay in services/domain code.
- Repeated status reads after expiration do not write additional audit rows because only `WAITING_OTHER_EVENT` traces are eligible for lazy expiration.

## Phase 8 - Hurl E2E, Final Docs, and Verification

### Prompt Used

```text
Create Hurl end-to-end tests and final verification docs for the public API of the Event Watchdog service.

Base URL for the Hurl tests: http://localhost:8081/api/v1

Create these Hurl scenario files under hurl/:
- started-flow.hurl
- waiting-other-event-flow.hurl
- completed-flow.hurl
- ttl-expired-flow.hurl
- unexpected-event-conflict.hurl
- duplicate-idempotent-flow.hurl
- duplicate-conflict.hurl
- duplicate-lazy-expiration.hurl
- late-event-conflict.hurl
- expected-event-flow.hurl
- error-final-event-flow.hurl
- unknown-trace.hurl

Rules for Hurl scenarios:
- Validate only the public HTTP API responses (POST /events and GET /traces/{traceId}/status).
- Do not assert internal database details.
- Use unique and readable phase8-* eventId and traceId prefixes.
- For TTL expiration, use an occurredAt timestamp sufficiently in the past instead of adding sleep calls.
- Each file should be self-contained.

Also update README.md with:
- Final API examples using the full /api/v1 routes and correct port 8081.
- Final data model documentation.
- Verification commands for Maven, app startup, and Hurl execution.
- Full list of Hurl scenario files and what each validates.

Mark Phase 8 as complete in TASKS.md and update AI_USAGE.md with accepted/rejected suggestions.
```

### Accepted Suggestions

- Created a dedicated `phase-8-e2e-final-verification` branch from updated `develop` after Phase 7 was merged.
- Added Hurl end-to-end scenarios under `hurl/` for the required public HTTP flows and the documented duplicate/late/unexpected decisions.
- Kept Hurl tests focused on the versioned public API routes: `/api/v1/events` and `/api/v1/traces/{traceId}/status`.
- Used fixed `phase8-*` event and trace IDs so each scenario is readable and easy to debug.
- Documented the need to run Hurl scenarios against a clean local database because the API intentionally treats duplicate `eventId` values as idempotent or conflicting instead of overwriting data.
- Updated README with final API examples, data model notes, and verification commands.
- Ran `./mvnw clean verify`: 35 tests passed and Spotless checks passed.
- Started the app locally with Docker/PostgreSQL and verified `GET /api/v1/health` returned `200`.
- Reset the event tables through the PostgreSQL Docker container before running E2E scenarios.
- Ran `hurl --test hurl/*.hurl`: 12 files and 29 requests passed after adding explicit expected-event, duplicate-conflict, final-error, and duplicate-triggered lazy-expiration coverage.

### Rejected or Adjusted Suggestions

- Did not add a Maven Hurl plugin because the project has no existing Hurl integration and the challenge asks for Hurl E2E files, not build-time Hurl execution.
- Did not remove `/v1` endpoint versioning; the final contract intentionally uses path versioning under `/api/v1`.

