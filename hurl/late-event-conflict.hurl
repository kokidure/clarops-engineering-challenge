POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-late-001",
  "traceId": "phase8-late-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2020-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 60
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-late-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedBefore" == "2020-06-15T10:01:00Z"

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-late-002",
  "traceId": "phase8-late-trace",
  "eventName": "RULES_EVALUATED",
  "result": "SUCCESS",
  "occurredAt": "2020-06-15T10:01:01Z"
}
HTTP 409
[Asserts]
jsonpath "$.code" == "EVENT_CONFLICT"
jsonpath "$.traceId" == "phase8-late-trace"
jsonpath "$.message" == "Expected event arrived after the TTL deadline"

GET http://localhost:8081/api/v1/traces/phase8-late-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-late-trace"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.eventsReceived" == 1
