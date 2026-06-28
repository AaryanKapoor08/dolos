-- Phase 2C: store the score detail alert-service fetches synchronously from scoring-service over gRPC.
--
-- New migration (never edit an applied V1). Nullable: an alert raised while scoring-service is
-- unreachable still persists — its detail is the Resilience4j fallback ("details unavailable").

ALTER TABLE alerts ADD COLUMN detail TEXT;
