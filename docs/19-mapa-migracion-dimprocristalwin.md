# 19 — Mapa de migración desde DimproCristalWin

Fecha de corte: 1 de octubre de 2026

Este documento cruza el menú principal de DimproCristalWin (`MENUS/PRINCIPAL.MPR`, Visual FoxPro) con lo que PERA tiene hoy en el repositorio. Sirve para decidir qué se migra, en qué orden y qué se queda fuera del núcleo horizontal. No es un contrato de requisitos: cada bloque debe validarse con usuarios antes de implementarse, y no autoriza a copiar código, tablas ni datos del programa legado.

## Criterio de estado

- **Cubierto**: PERA tiene persistencia, API e interfaz para la capacidad equivalente.
- **Parcial**: existe una parte (a menudo solo el modelo de datos) pero falta API, pantalla o reglas.
- **Pendiente**: no hay nada equivalente en el repositorio.
- **Vertical**: es propio del sector del vidrio o del aluminio. Según la [auditoría de generalización](08-generalizacion-erp-horizontal.md), no entra en el núcleo; si se recupera, será una extensión opcional.

El legado tiene unos 375 formularios y varias decenas de carpetas de informes. Este mapa trabaja a nivel de opción de menú, no de formulario.

## Archivo (maestros)

| Opción en DimproCristalWin | Estado en PERA | Dónde / qué falta |
|---|---|---|
| Empresa | Cubierto | `identity-service`, `/configuracion`. |
| Usuarios y grupos de usuarios | Cubierto | Usuarios, roles y permisos en `identity-service`, `/usuarios`. Los roles son fijos; no hay editor de grupos. |
| Tarifas, grupos de tarifas, tarifas especiales y su desglose | Cubierto | Tarifas, reglas y precios por cliente en `master-data-service`, `/maestros`. |
| Grupo descuento | Parcial | Las reglas de tarifa cubren descuentos; no hay un maestro de grupos de descuento como tal. |
| Artículos, familias, tipos, agrupación | Cubierto | Jerarquía naturaleza → supertipo → tipo → grupo, `/catalogo` y `/maestros`. |
| Almacén y diario de almacén | Parcial | Almacenes, existencias, diario, ajustes y traspasos en `/almacen` ([doc 20](20-compras-inventario.md)). El albarán de venta descuenta existencias. Sin valoración de inventario. |
| Clientes, grupos y tipos de cliente | Cubierto | Ficha con grupo, tipo, comercial, forma de entrega, motivo de baja, contactos, direcciones de entrega y notas en `/clientes` ([doc 23](23-ficha-de-cliente.md)). |
| Proveedores | Cubierto | `/proveedores`. |
| Formas de pago | Cubierto | `finance-service`, `/finanzas`. |
| Vendedores, cobradores, comerciales | Parcial | Maestro de comerciales con comisión por defecto y asignación al cliente ([doc 23](23-ficha-de-cliente.md)). Sin cobradores ni cálculo de comisiones. |
| Rutas, transportes, forma de entrega | Cubierto | Rutas, transportistas y vehículos en `operations-service`, `/operaciones`. |
| Actualiza riesgo clientes | Parcial | Riesgo de crédito en ventas; no hay recálculo masivo. |
| Obras de clientes | Parcial | `WorkSite` existe pero está congelado, sin API ni pantalla. Pendiente decidir si se generaliza como proyecto o ubicación de servicio. |
| Códigos postales | Pendiente | |
| Agenda telefónica | Cubierto | Listín de contactos en `/agenda` ([doc 22](22-reclamaciones-y-agenda.md)). Sin importador desde el programa anterior. |
| Bancos y agencias | Parcial | IBAN opcional en terceros; no hay maestro de bancos ni cuentas de la empresa. |
| Tipo de documentos y estados | Cubierto | Tipos y estados fijos en `sales-service`. No son configurables por el usuario. |
| Series de documentos | Cubierto | Numeraciones configurables, `/configuracion`. |
| Diario de caja, claves y cuentas contables | Cubierto | Plan de cuentas y libro diario en `/contabilidad`. Cajas, sesiones, diario y arqueo en `/cartera` ([doc 21](21-cartera-y-caja.md)). Sin claves de apunte configurables. |
| Monedas | Cubierto | Monedas y tipos de cambio, `/configuracion`. |
| Calendario de facturación | Pendiente | |
| Costos (elementos, recursos, tareas, escalas) | Pendiente | No hay escandallo ni costes. |
| Operarios / elementos, tipos de trabajo | Parcial | Los workflows configurables cubren pasos de trabajo; no hay maestro de operarios. |
| Medidor / montador, días de entrega por metros | Vertical | |
| Formas y figuras, catálogos de figuras | Vertical | |
| Cámaras de doble acristalamiento, láminas y pinturas | Vertical | |
| Control de caballetes | Vertical | Los embalajes retornables de PERA pueden cubrir parte del concepto de forma genérica. |

## Documentos

| Opción en DimproCristalWin | Estado en PERA | Dónde / qué falta |
|---|---|---|
| Ventas: creación, consulta y búsqueda | Cubierto | Presupuestos, pedidos, albaranes y facturas en `/presupuestos` y `/ventas`. |
| Facturación automática | Pendiente | No hay facturación por lotes de albaranes pendientes. |
| Documentos borrados | Parcial | El historial de auditoría registra las mutaciones; no hay papelera ni recuperación. |
| Compras: creación y consulta | Parcial | Pedido, albarán de entrada y factura de proveedor en `/compras` ([doc 20](20-compras-inventario.md)). Sin pagos a proveedores ni recepciones parciales. |
| Diario de producción | Parcial | Ejecuciones de workflow en `/operaciones`. Sin partes de producción ni consumo de materiales. |

## Listados, estadísticas y búsqueda

| Opción en DimproCristalWin | Estado en PERA | Dónde / qué falta |
|---|---|---|
| Listados de artículos, documentos, clientes y proveedores | Parcial | Centro de informes imprimibles en `/impresion`. La cobertura es mucho menor que la del legado. |
| Etiquetas (artículos, caballetes) | Pendiente | |
| Estadísticas por año/mes y comparativas | Parcial | Dashboard económico comparativo. Sin desglose por artículo, proveedor ni vendedor. |
| Búsqueda en árbol | Pendiente | Las pantallas tienen filtros propios; no hay una búsqueda jerárquica transversal. |

## Herramientas

| Opción en DimproCristalWin | Estado en PERA | Dónde / qué falta |
|---|---|---|
| Traspasar datos, cambio de precios desde Excel | Parcial | Importación CSV/Excel de clientes, proveedores y productos. Sin actualización masiva de precios. |
| Seguimiento de documentos, diarios de proceso | Cubierto | `activity-service`, `/historial`. |
| Traducción de formularios e informes | Parcial | Interfaz ES/EN. Los errores del backend siguen en español. |
| Carga de bancos y sucursales | Pendiente | |
| Pasar al histórico, archivado de acumulados | Pendiente | No hay cierre ni archivado de ejercicios. |
| Rehacer contadores | Cubierto | Las numeraciones se gestionan por serie; no hace falta la utilidad. |
| Traspaso a Lisec, OptyWay | Vertical | Integraciones con maquinaria de corte de vidrio. |

## Otros menús

| Opción en DimproCristalWin | Estado en PERA | Dónde / qué falta |
|---|---|---|
| Transporte: tipos, transportistas, salidas | Cubierto | Expediciones y fletes en `/operaciones`. |
| Agenda | Cubierto | Citas por semana con tipos, persona, cliente y contacto en `/agenda` ([doc 22](22-reclamaciones-y-agenda.md)). Sin festivos ni recordatorios. |
| Reclamaciones y no conformidades | Cubierto | `/reclamaciones` con las siete tablas de clasificación, seguimiento y cierre ([doc 22](22-reclamaciones-y-agenda.md)). Sin «crea documento» ni listados. |
| Aluminio: series y vidrios | Vertical | |
| iPedidos | Pendiente | Entrada de pedidos desde un canal externo. Requiere definir primero una API pública de pedidos. |
| VidrioService: VS-DELIVERY, VS-STOCK | Vertical | |

## Capacidades del legado que no aparecen en el menú

- **IGIC.** El legado mantiene una rama separada para Canarias, con el impuesto por artículo o por documento según una propiedad de empresa. PERA tiene un catálogo fiscal genérico por país; falta comprobar que cubre IGIC y recargo de equivalencia sin duplicar el producto en dos ramas.
- **Cartera.** Recibos, vencimientos y remesas viven en un programa aparte (DimproCartera). PERA los cubre en `/cartera` ([doc 21](21-cartera-y-caja.md)), todavía sin fichero para el banco ni pagos a proveedores.
- **Informes por cliente.** Cada cliente del legado tiene sus propios formatos de factura, con y sin logo. PERA genera un único PDF de factura con el logo de la empresa; no hay plantillas configurables.
- **Factura electrónica.** El legado genera Factura-e y presenta Veri*Factu a través de Verifacti o B2BRouter. PERA presenta Veri*Factu directamente a la AEAT, de forma automática y en pruebas o producción ([doc 24](24-verifactu-remision-automatica.md)); B2Brouter sigue solo en pruebas y no hay Factura-e.
- **Propiedades de empresa.** El legado activa comportamientos por empresa con propiedades numeradas. En PERA esas variaciones deben modelarse como parámetros de empresa con nombre, no como interruptores numerados.

## Orden de trabajo propuesto

1. **Compras e inventario básico.** Hecho en su alcance básico ([doc 20](20-compras-inventario.md)), incluida la salida de almacén al confirmar el albarán de venta.
2. **Cartera completa.** Hecho ([doc 21](21-cartera-y-caja.md)): recibos, remesas sin fichero bancario y caja. El modelo que existía era solo un esqueleto y hubo que diseñarlo.
3. **Facturación automática y cierre de ejercicio.** Operativa mensual que hoy se hace en el legado.
4. **Comerciales y comisiones, grupos de cliente.**
5. **Reclamaciones y agenda.** Hecho ([doc 22](22-reclamaciones-y-agenda.md)), con el listín.
6. **Plantillas de documento configurables y etiquetas.**
7. **Extensión vertical de vidrio.** Figuras, cámaras, caballetes, optimización de corte. Solo después de cerrar los bloques comunes, y como módulo opcional.

La migración de datos (DBF a PostgreSQL) es un trabajo distinto de la paridad funcional y necesita su propio plan por tabla.
