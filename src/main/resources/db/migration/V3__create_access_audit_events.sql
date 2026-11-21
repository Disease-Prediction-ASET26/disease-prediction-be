-- Lab 9, Part B: Personal Data Access Guard (see docs repo, mdd/iteration6/README.md, "Part B").
-- One row per guarded call: who did what to which kind of personal data, and whether it was
-- allowed. Rows hold ids, codes and the role only: no tokens, names, emails or symptoms.
-- actor_user_id has no foreign key on purpose: the audit row must outlive the account it describes
-- (retention after account deletion is still an open point, see the design).
CREATE TABLE access_audit_events (
    id            UUID         PRIMARY KEY,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id UUID,
    actor_role    VARCHAR(20)  CHECK (actor_role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    action        VARCHAR(20)  NOT NULL CHECK (action IN ('READ', 'CREATE', 'UPDATE')),
    resource_type VARCHAR(40)  NOT NULL
        CHECK (resource_type IN ('PATIENT_PROFILE', 'CONSENT', 'ASSESSMENT')),
    resource_id   UUID,
    outcome       VARCHAR(10)  NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED', 'FAILED')),
    reason_code   VARCHAR(64)
);

CREATE INDEX idx_access_audit_events_actor_occurred_at
    ON access_audit_events (actor_user_id, occurred_at);

-- Append-only: the application can add audit rows but never rewrite or remove them.
CREATE FUNCTION reject_access_audit_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'access_audit_events is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER access_audit_events_append_only
    BEFORE UPDATE OR DELETE ON access_audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_access_audit_change();

CREATE TRIGGER access_audit_events_no_truncate
    BEFORE TRUNCATE ON access_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_access_audit_change();
