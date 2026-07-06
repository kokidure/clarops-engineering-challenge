POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-expired-001",
  "traceId": "phase8-duplicate-expired-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2020-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 1,
  "metadata": {
    "scenario": "duplicate-lazy-expiration"
  }
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-expired-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedBefore" == "2020-06-15T10:00:01Z"

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-duplicate-expired-001",
  "traceId": "phase8-duplicate-expired-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2020-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 1,
  "metadata": {
    "scenario": "duplicate-lazy-expiration"
  }
}
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-expired-trace"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 1

GET http://localhost:8081/api/v1/traces/phase8-duplicate-expired-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-duplicate-expired-trace"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.eventsReceived" == 1
