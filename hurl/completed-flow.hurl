POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-completed-001",
  "traceId": "phase8-completed-trace",
  "eventName": "TRANSFER_SETTLED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "finalEvent": true,
  "metadata": {
    "scenario": "completed-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-completed-trace"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventName" == "TRANSFER_SETTLED"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-completed-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-completed-trace"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.eventsReceived" == 1
