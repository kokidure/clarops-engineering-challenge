GET http://localhost:8081/api/v1/traces/phase8-unknown-trace/status
HTTP 404
[Asserts]
jsonpath "$.code" == "TRACE_NOT_FOUND"
jsonpath "$.traceId" == "phase8-unknown-trace"
jsonpath "$.message" == "Trace not found: phase8-unknown-trace"
