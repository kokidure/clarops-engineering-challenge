POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-error-final-001",
  "traceId": "phase8-error-final-trace",
  "eventName": "TRANSFER_FAILED",
  "result": "ERROR",
  "occurredAt": "2026-06-15T10:00:00Z",
  "finalEvent": true,
  "metadata": {
    "scenario": "error-final-event-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-error-final-trace"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventName" == "TRANSFER_FAILED"
jsonpath "$.lastEventResult" == "ERROR"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-error-final-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-error-final-trace"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventResult" == "ERROR"
jsonpath "$.eventsReceived" == 1
