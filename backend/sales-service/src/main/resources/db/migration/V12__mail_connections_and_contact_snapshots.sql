ALTER TABLE commercial_documents ADD COLUMN customer_email_snapshot varchar(254);
ALTER TABLE commercial_documents ADD COLUMN customer_address_snapshot varchar(600);
CREATE TABLE mail_connections (
 company_id uuid PRIMARY KEY, host varchar(253) NOT NULL, port integer NOT NULL,
 username varchar(254) NOT NULL, password_cipher text NOT NULL,
 sender_email varchar(254) NOT NULL, sender_name varchar(180) NOT NULL,
 security varchar(20) NOT NULL CHECK (security IN ('STARTTLS','TLS')),
 enabled boolean NOT NULL DEFAULT false, auto_invoices boolean NOT NULL DEFAULT false,
 verified_at timestamptz, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE document_mail (
 id uuid PRIMARY KEY, company_id uuid NOT NULL, document_id uuid NOT NULL,
 recipient varchar(254), subject varchar(240) NOT NULL, filename varchar(180), attachment bytea,
 status varchar(20) NOT NULL CHECK (status IN ('PENDING','SENDING','SENT','FAILED','UNKNOWN')),
 error varchar(500), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 sent_at timestamptz, UNIQUE(company_id,document_id)
);
CREATE INDEX document_mail_pending ON document_mail(created_at) WHERE status = 'PENDING';
