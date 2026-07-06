POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-expired-001",
  "traceId": "phase8-expired-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2020-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 1,
  "metadata": {
    "scenario": "ttl-expired-flow"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-expired-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedBefore" == "2020-06-15T10:00:01Z"

GET http://localhost:8081/api/v1/traces/phase8-expired-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-expired-trace"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-expired-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-expired-trace"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.eventsReceived" == 1
