# Formas de pago como en DimproCristalWin (borrador de diseño, sin implementar)

Análisis hecho leyendo `FORMS/documentos.SCT` (`calcula_dias_pago`, `calcula_fechavto`, `mira_dias_pago`, `mira_dia_festivo_vencimiento`) y las tablas `forma_pago`, `escala_forma_pago`, `fpago_cliente` de varias instalaciones reales.

## Lo que ya tiene PERA

Finance: `payment_methods` (código, nombre, activa) y `payment_schedule_rules` (plazo n, días, %). Solo alta y listado (`POST/GET /api/v1/payment-methods`), sin edición. `PaymentScheduleCalculator` suma días naturales, redondea a 4 decimales y deja la diferencia en el último plazo. El cliente guarda `defaultPaymentMethodId` (master-data). Ventas solo lee el nombre para imprimirlo.

## Lo que hace Dimpro y falta

| Dimpro | En PERA |
|---|---|
| `NUM_RECIS` (0–5, 0 = 1) y `DIAS_VTO1..5` | Ya cubierto por las reglas (días + %). |
| Importe: `ROUND(total / n, 2)` en cada recibo; la diferencia de redondeo va al **primero** | Cambiar el calculador: 2 decimales y diferencia al primer plazo. |
| `NEGOCIABLE` (remesable), `ACEPTABLE`, `ALA_ORDEN`, `TRANSFEREN` | Columnas booleanas nuevas (finance `V8`). |
| `CONCEPTO` (concepto contable: CO, RG, RP, CV, TS, RD, PA, CF…) | Columna `accounting_concept` (4). |
| `FPAGO_FACT` (Facturae: 01 contado, 03 recibo, 04 transferencia, 05 letra aceptada, 06 crédito documentario, 08 letra pendiente de aceptar, 10 pagaré no a la orden, 12 reposición, 18 contra reembolso) | Columna `facturae_code` (lista oficial 01–19). |
| `ESCALADO` + `escala_forma_pago` (desde, hasta, forma destino): según el total de la factura se usa otra forma de pago y se cambia en el documento | Tabla `payment_method_scales`; al generar vencimientos se elige la escala con `desde <= total <= hasta`. |
| Empresa `calculos_dias_vto`: meses de 30 días | Ajuste por empresa en finance. Regla copiada: días múltiplo de 30 → se suman meses conservando el día (31 pasa a 30; febrero como mucho 28); no múltiplo y ≥ 30 → `gomonth(fecha, días÷30) + (resto + 1)`; menos de 30 → días naturales. |
| Cliente `DIAS_PAGO` (hasta 3 días) | Master-data `V13` (`payment_day1..3`). Tras sumar los días, se lleva al primer día de pago ≥ el día del vencimiento en ese mes (recortado al fin de mes: `calcula_fechavto`); si no hay, al primer día de pago del mes siguiente. |
| Empresa `ajustar_vto_dia` (`mira_dias_pago`) | Pendiente: la rutina de Dimpro da resultados raros; preguntar a Javier antes de copiarla. |
| Vacaciones del cliente (opciones 1–5) y festivos de la agenda (`prop_emp_233` días) | Siguiente fase. |
| `fpago_cliente`: varias formas por cliente, una principal | Siguiente fase. |
| Lista de formas | Cada instalación tiene la suya (data01 44, Cristalant 43, Hnos Salamanca 35, Macarosa 3): ofrecer «Cargar formas habituales» (Contado, Transferencia, Recibo 30/60/90, 30-60, 30-60-90, Pagaré 60, Confirming 60, Tarjeta, Reposición). |

## Orden de trabajo previsto

1. Finance `V8`: campos nuevos, escalas y ajuste de meses de 30 días; `PUT /api/v1/payment-methods/{id}`; formas habituales.
2. Calculador nuevo con pruebas de los casos de Dimpro; `GenerateDueDatesRequest` con `customerId` opcional para leer los días de pago del cliente en master-data.
3. Master-data `V13`: días de pago en la ficha de cliente.
4. Pantallas: Finanzas (ficha completa de la forma de pago) y Clientes (días de pago).
