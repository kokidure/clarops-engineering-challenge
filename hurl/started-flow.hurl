POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-started-001",
  "traceId": "phase8-started-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "scenario": "started-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-started-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventName" == "APPLICATION_RECEIVED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-started-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-started-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.eventsReceived" == 1
