-- Fotografía explícita del estado previo. No inventa quién hizo una operación antigua
-- ni asegura que existiera una transmisión bancaria.
WITH recovered AS (
    SELECT gen_random_uuid() AS event_id, r.*,
           (SELECT count(*) FROM receipts p WHERE p.company_id = r.company_id AND p.remittance_id = r.id) AS receipt_count
    FROM remittances r WHERE r.status <> 'DRAFT'
)
INSERT INTO remittance_audit_outbox(event_id, company_id, payload)
SELECT event_id, company_id, jsonb_build_object(
    'eventId', event_id, 'companyId', company_id, 'occurredAt', CURRENT_TIMESTAMP,
    'sourceService', 'finance-service', 'eventType', 'BUSINESS_ACTIVITY',
    'actorUserId', NULL, 'actorName', NULL, 'action', 'RECOVER_STATE',
    'resourceType', 'REMITTANCE', 'resourceId', id, 'outcome', 'SUCCESS',
    'correlationId', NULL, 'metadata', jsonb_build_object(
        'remittanceNumber', remittance_number, 'totalAmount', total_amount,
        'currencyCode', currency_code, 'receiptCount', receipt_count,
        'statusTo', status, 'sentDate', sent_date, 'settlementDate', settlement_date,
        'bankTransmission', false, 'historicalActorUnknown', true)
)::text FROM recovered;
