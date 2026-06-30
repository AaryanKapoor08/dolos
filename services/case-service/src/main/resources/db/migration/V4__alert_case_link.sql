-- Phase 3E: the alert→case idempotency link.
--
-- Keyed on the alert id so the intake consumer opens at most one case per AlertRaised: a redelivery
-- finds the row already present and skips. It is written in the SAME transaction as the OpenCase
-- command, so a case is never left without its link (nor a link without its case).
CREATE TABLE alert_case_link (
    alert_id   UUID         PRIMARY KEY,
    case_id    UUID         NOT NULL,
    opened_at  TIMESTAMPTZ  NOT NULL
);
