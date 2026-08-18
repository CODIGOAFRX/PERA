ALTER TABLE alert_rules
    ADD COLUMN delivery_channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    ADD CONSTRAINT ck_alert_rule_delivery_channel CHECK (delivery_channel IN ('IN_APP'));

ALTER TABLE alert_instances
    DROP CONSTRAINT fk_alert_event_tenant,
    ADD CONSTRAINT fk_alert_event_tenant FOREIGN KEY (company_id, source_event_id)
        REFERENCES audit_events(company_id, id) ON DELETE CASCADE;

CREATE OR REPLACE FUNCTION reject_audit_event_mutation()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('pera.audit_retention_cleanup', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION purge_expired_audit_events(p_cutoff TIMESTAMPTZ, p_batch_size INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    IF p_cutoff IS NULL OR p_batch_size < 1 OR p_batch_size > 10000 THEN
        RAISE EXCEPTION 'invalid audit retention arguments';
    END IF;

    PERFORM set_config('pera.audit_retention_cleanup', 'on', true);
    WITH doomed AS (
        SELECT event.id
        FROM audit_events event
        WHERE event.occurred_at < p_cutoff
          AND NOT EXISTS (
              SELECT 1 FROM alert_instances alert
              WHERE alert.source_event_id = event.id
                AND alert.status <> 'RESOLVED'
          )
        ORDER BY event.occurred_at, event.id
        LIMIT p_batch_size
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM audit_events event
    USING doomed
    WHERE event.id = doomed.id;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

REVOKE ALL ON FUNCTION purge_expired_audit_events(TIMESTAMPTZ, INTEGER) FROM PUBLIC;

COMMENT ON FUNCTION purge_expired_audit_events(TIMESTAMPTZ, INTEGER) IS
    'Borra por lotes eventos vencidos sin alertas abiertas; es la única excepción a la inmutabilidad.';
