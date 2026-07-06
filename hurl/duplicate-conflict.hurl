POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-conflict-001",
  "traceId": "phase8-duplicate-conflict-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "attempt": "original"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-conflict-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.eventsReceived" == 1

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-conflict-001",
  "traceId": "phase8-duplicate-conflict-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "ERROR",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "attempt": "changed"
  }
}
HTTP 409
[Asserts]
jsonpath "$.code" == "EVENT_CONFLICT"
jsonpath "$.traceId" == "phase8-duplicate-conflict-trace"
jsonpath "$.message" == "Duplicate eventId has different payload"

GET http://localhost:8081/api/v1/traces/phase8-duplicate-conflict-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-conflict-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.eventsReceived" == 1
