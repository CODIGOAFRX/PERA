ALTER TABLE commercial_documents
    ADD COLUMN quote_status VARCHAR(30),
    ADD COLUMN quote_valid_until DATE,
    ADD COLUMN quote_decided_at TIMESTAMPTZ,
    ADD COLUMN quote_rejection_reason VARCHAR(500);

UPDATE commercial_documents
SET quote_status = CASE status
        WHEN 'DRAFT' THEN 'DRAFT'
        WHEN 'CONFIRMED' THEN 'SENT'
        WHEN 'CONVERTED' THEN 'CONVERTED'
        WHEN 'CANCELLED' THEN 'REJECTED'
    END,
    quote_valid_until = issue_date + 30
WHERE document_type = 'QUOTE';

ALTER TABLE commercial_documents
    ADD CONSTRAINT ck_document_quote_fields CHECK (
        (document_type = 'QUOTE' AND quote_status IS NOT NULL AND quote_valid_until IS NOT NULL)
        OR (document_type <> 'QUOTE' AND quote_status IS NULL AND quote_valid_until IS NULL
            AND quote_decided_at IS NULL AND quote_rejection_reason IS NULL)
    ),
    ADD CONSTRAINT ck_document_quote_status CHECK (
        quote_status IS NULL OR quote_status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED')
    ),
    ADD CONSTRAINT ck_document_quote_validity CHECK (
        quote_valid_until IS NULL OR quote_valid_until >= issue_date
    );

CREATE INDEX idx_documents_quote_inbox
    ON commercial_documents(company_id, quote_status, quote_valid_until DESC)
    WHERE document_type = 'QUOTE';
