CREATE TABLE fiscal_connections (
 company_id uuid NOT NULL, provider varchar(12) NOT NULL CHECK(provider IN ('AEAT','B2B')),
 account varchar(80) NOT NULL DEFAULT '', secret_cipher text NOT NULL,
 enabled boolean NOT NULL DEFAULT false, next_send_at timestamptz,
 PRIMARY KEY(company_id,provider)
);
CREATE TABLE fiscal_deliveries (
 company_id uuid NOT NULL, provider varchar(12) NOT NULL, source_id uuid NOT NULL,
 state varchar(40) NOT NULL, remote_id varchar(80), account varchar(80) NOT NULL,
 message text NOT NULL DEFAULT '', response text, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(company_id,provider,source_id)
);
