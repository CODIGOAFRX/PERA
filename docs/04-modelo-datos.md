# Modelo de datos

## Convenciones

- IDs UUID generados por la aplicación.
- Fechas de auditoría UTC (`created_at`, `updated_at`) y bloqueo optimista (`version`).
- Importes `NUMERIC(19,4)`; cantidades admiten seis decimales.
- Nombres SQL en inglés para alinearlos con el código; la API y documentación funcional pueden permanecer en español.

## Identidad

- `companies`
- `app_users`
- `permissions`
- `roles`, `role_permissions`
- `user_companies`, `user_company_roles`

La pertenencia es el agregado de autorización: une usuario y empresa y contiene sus roles para esa empresa.

## Maestros

- `parties`: datos comunes de cualquier tercero.
- `customer_profiles`, `supplier_profiles`: extensiones sin duplicar identidad fiscal.
- `party_addresses`, `party_contacts`.
- `customer_notes`, `customer_special_rates`, `customer_specific_prices`, `work_sites`.
- `product_types`, `product_families`, `product_categories`, `products`.
- `price_lists`, `price_list_items`.

## Ventas

- `commercial_documents`: cabecera y snapshots de cliente.
- `document_lines`: snapshots de artículo y cantidades/importes.
- `document_sequences`: numeración bloqueada por empresa, tipo y ejercicio.
- `outbox_events`: integración fiable sin doble escritura.

Un documento convertido conserva `source_document_id`; no se reemplaza ni muta el tipo del origen.

## Finanzas

- `payment_methods`, `payment_schedule_rules`.
- `document_due_dates`.
- `receipts`.
- `remittances`, `remittance_receipts`.
- `financial_movements`.
- `customer_risks`.
- `cash_registers`, `cash_sessions`, `cash_movements`.

## Propiedad y referencias

Los UUID de cliente, documento o forma de pago que cruzan contextos son referencias externas, no relaciones JPA ni claves foráneas entre bases. Los nombres, códigos y precios necesarios para un histórico se copian como snapshots en el agregado que los consume.
