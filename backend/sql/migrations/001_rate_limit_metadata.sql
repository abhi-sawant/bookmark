-- 001: allow the link-preview and link-check endpoints to be rate limited.
-- Run once on an existing database (fresh installs already get this from schema.sql).
-- Safe to run twice: MODIFY to the same definition is a no-op.
ALTER TABLE rate_limit_events
    MODIFY action ENUM('register', 'login', 'forgot_password', 'metadata') NOT NULL;
