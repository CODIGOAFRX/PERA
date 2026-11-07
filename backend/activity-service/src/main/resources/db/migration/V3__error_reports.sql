-- Avisos de error del programa, como el ERRORES.LOG y los correos de error de DimproCristalWin:
-- qué pasó, a quién, en qué pantalla y con qué versión. Se guardan siempre y se mandan a soporte
-- por correo si la instalación lo tiene configurado.
CREATE TABLE error_reports (
    id            UUID PRIMARY KEY,
    company_id    UUID,
    company_name  VARCHAR(180),
    user_id       VARCHAR(80),
    user_name     VARCHAR(180),
    source        VARCHAR(20)  NOT NULL CHECK (source IN ('PANTALLA', 'SERVIDOR')),
    screen        VARCHAR(300),
    app_version   VARCHAR(30),
    error_id      VARCHAR(40),
    message       VARCHAR(1000) NOT NULL,
    detail        TEXT,
    user_agent    VARCHAR(400),
    occurrences   INTEGER      NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    emailed_at    TIMESTAMPTZ,
    email_error   VARCHAR(500)
);
CREATE INDEX ix_error_reports_company_seen ON error_reports (company_id, last_seen_at DESC);
