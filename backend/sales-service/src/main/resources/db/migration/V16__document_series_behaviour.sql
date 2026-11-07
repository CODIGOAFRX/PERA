-- Series de documentos como las de DimproCristalWin (tabla fseries): cada numeración pasa a ser una serie con su
-- comportamiento, no solo su contador. Primera parte: lo general, válido para cualquier sector. Los valores por
-- defecto dejan las series existentes funcionando exactamente igual que antes.
ALTER TABLE numbering_schemes
    ADD COLUMN display_order         INTEGER      NOT NULL DEFAULT 0,      -- fseries.orden
    ADD COLUMN color                 VARCHAR(16),                          -- fseries.color_grid
    ADD COLUMN listing_group         VARCHAR(60),                          -- agrupación para listados (id_agrupa)
    ADD COLUMN next_scheme_id        UUID,                                 -- «Pasará al documento…» (id_pasar_a)
    ADD COLUMN same_number_as_source BOOLEAN      NOT NULL DEFAULT FALSE,  -- numdocu_igual
    ADD COLUMN same_date_as_source   BOOLEAN      NOT NULL DEFAULT FALSE,  -- ffra_igual_falb
    ADD COLUMN asks_number           BOOLEAN      NOT NULL DEFAULT FALSE,  -- pide_numdocu
    ADD COLUMN carries_notes         BOOLEAN      NOT NULL DEFAULT TRUE,   -- pasa_nota_docu
    ADD COLUMN footer_text           VARCHAR(200),                         -- texto_documento
    ADD COLUMN print_copies          SMALLINT     NOT NULL DEFAULT 0,      -- copias_doc
    ADD COLUMN priced                BOOLEAN      NOT NULL DEFAULT TRUE,   -- documento_valorado
    ADD COLUMN internal              BOOLEAN      NOT NULL DEFAULT FALSE,  -- interno
    ADD COLUMN zero_amounts          BOOLEAN      NOT NULL DEFAULT FALSE,  -- es_reposicion
    ADD COLUMN counts_for_risk       BOOLEAN      NOT NULL DEFAULT TRUE,   -- es_riesgo
    ADD COLUMN remind_after_days     SMALLINT     NOT NULL DEFAULT 0,      -- dias_aviso
    ADD CONSTRAINT ck_numbering_print_copies CHECK (print_copies BETWEEN 0 AND 9),
    ADD CONSTRAINT ck_numbering_remind_days CHECK (remind_after_days BETWEEN 0 AND 999);

-- Cada documento recuerda la serie con la que se hizo: de ella salen su impresión, a qué pasa, etc. Los antiguos
-- quedan sin serie y se comportan como la predeterminada de su tipo.
ALTER TABLE commercial_documents ADD COLUMN numbering_scheme_id UUID;
CREATE INDEX ix_commercial_documents_scheme ON commercial_documents (company_id, numbering_scheme_id);
