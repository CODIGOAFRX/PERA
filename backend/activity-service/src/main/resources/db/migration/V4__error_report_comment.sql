-- Como el «Comentario» de la ventana de error de DimproCristalWin: lo que el usuario estaba haciendo. El aviso
-- se manda cuando el usuario lo envía o, si cierra sin enviarlo, pasados unos minutos.
ALTER TABLE error_reports
    ADD COLUMN user_comment      VARCHAR(2000),
    ADD COLUMN send_attempted_at TIMESTAMPTZ,
    ADD COLUMN sent_with         VARCHAR(254);
