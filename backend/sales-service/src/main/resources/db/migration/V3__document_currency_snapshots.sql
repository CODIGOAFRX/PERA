ALTER TABLE commercial_documents
    ADD COLUMN base_currency VARCHAR(3),
    ADD COLUMN exchange_rate NUMERIC(19,10),
    ADD COLUMN exchange_rate_date DATE,
    ADD COLUMN exchange_rate_source VARCHAR(120),
    ADD COLUMN base_net_amount NUMERIC(19,4),
    ADD COLUMN base_tax_amount NUMERIC(19,4),
    ADD COLUMN base_total_amount NUMERIC(19,4);

UPDATE commercial_documents
SET base_currency = currency,
    exchange_rate = 1,
    exchange_rate_date = issue_date,
    exchange_rate_source = 'LEGACY_IDENTITY',
    base_net_amount = net_amount,
    base_tax_amount = tax_amount,
    base_total_amount = total_amount;

ALTER TABLE commercial_documents
    ALTER COLUMN base_currency SET NOT NULL,
    ALTER COLUMN exchange_rate SET NOT NULL,
    ALTER COLUMN exchange_rate_date SET NOT NULL,
    ALTER COLUMN exchange_rate_source SET NOT NULL,
    ALTER COLUMN base_net_amount SET NOT NULL,
    ALTER COLUMN base_tax_amount SET NOT NULL,
    ALTER COLUMN base_total_amount SET NOT NULL,
    ADD CONSTRAINT ck_document_exchange_rate CHECK (exchange_rate > 0);
