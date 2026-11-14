# Agent Loop Issues 1-5 Change Notes

## Reference documents

- Day 12 backend database design V1.4
- Day 12 backend default-module OpenAPI V1.4
- Day 12 software requirements specification

## Related source files

- `agent/access.py`: role data scope and project authorization.
- `agent/tools.py`: scoped device, alarm, and environment queries; health metrics; alarm trends.
- `agent/agent_loop.py`: tool orchestration for alarm analysis, device diagnosis, and safety suggestions.
- `agent/app.py`: session/message authorization, scope propagation, SSE idempotency, keepalive, and resume.

## Issue mapping

1. Data authorization: enforce `sys_role.data_scope` and filter readable project data.
2. Device health: add recent alarms, metric peak/average/range, and health level.
3. Alarm analysis: add hourly distribution, daily trend, and peak hour.
4. Safety suggestions: combine overview, alarm trends, and environment results.
5. SSE: add response locking, state re-check, archive idempotency, ping, `Last-Event-ID`, and unique event IDs.

## Verification

```text
python -m pytest agent/tests -q
11 passed
```
