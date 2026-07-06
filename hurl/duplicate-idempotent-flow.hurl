POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-001",
  "traceId": "phase8-duplicate-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "scenario": "duplicate-idempotent-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.eventsReceived" == 1

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-001",
  "traceId": "phase8-duplicate-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "scenario": "duplicate-idempotent-flow"
  }
}
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.eventsReceived" == 1
