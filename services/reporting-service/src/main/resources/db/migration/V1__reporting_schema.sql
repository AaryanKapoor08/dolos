-- Phase 6B: reporting-service owns the `reporting` schema (Flyway creates + migrates it, history table
-- lives here too), isolated from the other services sharing the `dolos` database.
--
-- This schema holds TWO things:
--   1. The Spring Batch metadata tables (job/step execution history) — the canonical Spring Batch 5
--      PostgreSQL DDL, created here by Flyway rather than by Batch's own initializer (which is set to
--      `never`, keeping Flyway the single owner of every table, ddl-auto style).
--   2. `filed_report` — the business output: one row per FILED SAR/STR, also read by Superset (6C).
--
-- Batch is configured with table-prefix `reporting.BATCH_`, so its DAOs resolve to these tables.

-- ----------------------------------------------------------------------------------------------------
-- Spring Batch 5 metadata tables (PostgreSQL).
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE TABLE BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL,
    END_TIME TIMESTAMP DEFAULT NULL,
    STATUS VARCHAR(10),
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED TIMESTAMP,
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT NOT NULL,
    PARAMETER_NAME VARCHAR(100) NOT NULL,
    PARAMETER_TYPE VARCHAR(100) NOT NULL,
    PARAMETER_VALUE VARCHAR(2500),
    IDENTIFYING CHAR(1) NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL,
    END_TIME TIMESTAMP DEFAULT NULL,
    STATUS VARCHAR(10),
    COMMIT_COUNT BIGINT,
    READ_COUNT BIGINT,
    FILTER_COUNT BIGINT,
    WRITE_COUNT BIGINT,
    READ_SKIP_COUNT BIGINT,
    WRITE_SKIP_COUNT BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT BIGINT,
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED TIMESTAMP,
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;

-- ----------------------------------------------------------------------------------------------------
-- Business output: one row per filed regulatory report.
-- ----------------------------------------------------------------------------------------------------
CREATE TABLE filed_report (
    report_ref     VARCHAR(80)  PRIMARY KEY,          -- deterministic, e.g. SAR-2026-07-01-4e5e8083
    alert_id       UUID         NOT NULL UNIQUE,       -- idempotency key: one filing per source alert
    account_id     VARCHAR(64)  NOT NULL,
    report_type    VARCHAR(8)   NOT NULL,              -- SAR (activity) / STR (single transaction)
    score          INTEGER      NOT NULL,
    business_date  DATE         NOT NULL,
    object_pointer VARCHAR(512) NOT NULL,              -- s3://filed-reports/<key> in MinIO
    narrative      TEXT         NOT NULL,
    filed_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Superset (6C) slices SAR volume over time + by account.
CREATE INDEX idx_filed_report_business_date ON filed_report (business_date);
CREATE INDEX idx_filed_report_account ON filed_report (account_id);
CREATE INDEX idx_filed_report_type ON filed_report (report_type);
