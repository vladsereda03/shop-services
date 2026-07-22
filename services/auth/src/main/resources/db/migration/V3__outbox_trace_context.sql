-- Carries the tracing context of the request that recorded the event (propagation headers as a
-- JSON map), so the asynchronous relay can resume the SAME distributed trace when it later
-- publishes to Kafka — otherwise the signup trace would split at the outbox boundary. Nullable:
-- events recorded outside any trace (or before this column existed) simply carry none.

ALTER TABLE outbox ADD COLUMN trace_context text;
