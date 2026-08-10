CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    company_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_service VARCHAR(80) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    actor_user_id UUID,
    actor_name VARCHAR(160),
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    outcome VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(100),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_audit_event_id UNIQUE (event_id),
    CONSTRAINT uk_audit_event_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_audit_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_events_search ON audit_events(company_id, occurred_at DESC);
CREATE INDEX idx_audit_events_resource ON audit_events(company_id, resource_type, resource_id);

CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    action VARCHAR(120),
    resource_type VARCHAR(100),
    condition_field VARCHAR(120),
    condition_operator VARCHAR(30),
    condition_value VARCHAR(240),
    severity VARCHAR(20) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    message_template VARCHAR(500) NOT NULL,
    cooldown_minutes INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_alert_rule_code UNIQUE (company_id, code),
    CONSTRAINT uk_alert_rule_company_id UNIQUE (company_id, id),
    CONSTRAINT ck_alert_rule_cooldown CHECK (cooldown_minutes >= 0),
    CONSTRAINT ck_alert_rule_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_alert_rule_operator CHECK (condition_operator IS NULL OR condition_operator IN (
        'EXISTS', 'NOT_EXISTS', 'EQUALS', 'NOT_EQUALS', 'CONTAINS',
        'GREATER_THAN', 'GREATER_THAN_OR_EQUAL', 'LESS_THAN', 'LESS_THAN_OR_EQUAL'
    )),
    CONSTRAINT ck_alert_rule_condition CHECK (
        (condition_field IS NULL AND condition_operator IS NULL AND condition_value IS NULL)
        OR (
            condition_field IS NOT NULL
            AND condition_operator IS NOT NULL
            AND (
                condition_value IS NOT NULL
                OR condition_operator IN ('EXISTS', 'NOT_EXISTS')
            )
        )
    )
);

CREATE TABLE alert_instances (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    source_event_id UUID NOT NULL,
    dedupe_key VARCHAR(300) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_alert_source_rule UNIQUE (source_event_id, rule_id),
    CONSTRAINT fk_alert_rule_tenant FOREIGN KEY (company_id, rule_id)
        REFERENCES alert_rules(company_id, id),
    CONSTRAINT fk_alert_event_tenant FOREIGN KEY (company_id, source_event_id)
        REFERENCES audit_events(company_id, id),
    CONSTRAINT ck_alert_instance_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_alert_instance_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE INDEX idx_alert_instances_inbox ON alert_instances(company_id, status, created_at DESC);
CREATE INDEX idx_alert_instances_dedupe ON alert_instances(company_id, rule_id, dedupe_key, created_at DESC);

CREATE OR REPLACE FUNCTION reject_audit_event_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
