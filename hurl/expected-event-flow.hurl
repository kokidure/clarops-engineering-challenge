POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-expected-001",
  "traceId": "phase8-expected-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2099-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-expected-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 1

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-expected-002",
  "traceId": "phase8-expected-trace",
  "eventName": "RULES_EVALUATED",
  "result": "SUCCESS",
  "occurredAt": "2099-06-15T10:01:00Z"
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-expected-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventName" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 2

GET http://localhost:8081/api/v1/traces/phase8-expected-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-expected-trace"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventName" == "RULES_EVALUATED"
jsonpath "$.eventsReceived" == 2
