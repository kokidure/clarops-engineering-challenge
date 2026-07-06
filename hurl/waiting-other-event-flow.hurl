POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-waiting-001",
  "traceId": "phase8-waiting-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2099-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "metadata": {
    "scenario": "waiting-other-event-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-waiting-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.nextExpectedBefore" == "2099-06-15T10:02:00Z"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-waiting-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-waiting-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 1
