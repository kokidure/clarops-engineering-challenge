POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-unexpected-001",
  "traceId": "phase8-unexpected-trace",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2099-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120
}
HTTP 201
[Asserts]
jsonpath "$.traceId" == "phase8-unexpected-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"

POST http://localhost:8081/api/v1/events
Content-Type: application/json

{
  "eventId": "phase8-unexpected-002",
  "traceId": "phase8-unexpected-trace",
  "eventName": "TRANSFER_SETTLED",
  "result": "SUCCESS",
  "occurredAt": "2099-06-15T10:01:00Z"
}
HTTP 409
[Asserts]
jsonpath "$.code" == "EVENT_CONFLICT"
jsonpath "$.traceId" == "phase8-unexpected-trace"
jsonpath "$.message" == "Unexpected event. Expected RULES_EVALUATED"

GET http://localhost:8081/api/v1/traces/phase8-unexpected-trace/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "phase8-unexpected-trace"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.eventsReceived" == 1
