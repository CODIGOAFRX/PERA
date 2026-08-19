# 12 — Veri*Factu en PERA: análisis y plan de implementación

> Estado: propuesta de trabajo. Ninguna línea de este documento sustituye a la fuente
> normativa. Antes de codificar cada bloque hay que abrir el PDF oficial correspondiente
> de la AEAT (enlaces al final) y validar formatos byte a byte.

---

## 1. Qué estamos implementando exactamente

Tres normativas distintas se confunden habitualmente. **Solo abordamos la primera.**

| | Qué es | Fuente | Estado |
|---|---|---|---|
| **Veri*Factu / SIF** | Registros de facturación encadenados con huella SHA-256, QR en la factura y remisión a la AEAT | RD 1007/2023 + Orden HAC/1177/2024 | **Es lo que hacemos** |
| Factura electrónica B2B | Formato estructurado (Facturae/UBL) entre empresas + estados de pago | Ley 18/2022 "Crea y Crece" | Fuera de alcance, pero no cerramos puertas en el modelo de datos |
| SII | Libros registro de IVA en tiempo real | RD 596/2016 | Fuera de alcance |

### Plazos vigentes

Los plazos se retrasaron con el **Real Decreto-ley 15/2025**:

- **1 de enero de 2027** — contribuyentes del Impuesto sobre Sociedades.
- **1 de julio de 2027** — resto de obligados (autónomos en IRPF).
- **29 de julio de 2025** — fecha ya pasada para **productores de software**: los programas
  ofrecidos deben permitir cumplir el reglamento.

Ese último punto nos afecta directamente. PERA se comercializa con `licensing-service`, así que
PERA es *productor de software* a efectos del art. 13 del RD 1007/2023 y le corresponde emitir
**declaración responsable** por el producto. El régimen sancionador para productores llega a
150.000 € por ejercicio y tipo de programa. Conviene que esto lo revise un asesor fiscal antes
de la primera venta con el módulo activo, no después.

---

## 2. Decisión de enfoque (recomendación)

**Modalidad: SOLO VERI*FACTU. Núcleo nativo en PERA, transporte a la AEAT detrás de una interfaz.**

### Por qué modalidad VERI*FACTU y no "NO VERI*FACTU"

La modalidad *no verifactu* (guardar los registros sin remitirlos) obliga adicionalmente a:

- firma electrónica de cada registro de facturación,
- **registro de eventos** completo y funcionando,
- conservación y accesibilidad de los registros durante el periodo de prescripción.

La modalidad VERI*FACTU, según las FAQ de desarrolladores de la AEAT, **no exige el registro de
eventos** (es voluntario) ni la firma de los registros, porque la remisión inmediata a la AEAT
cumple la función de garantía. Es bastante menos trabajo y menos superficie de error. Para un ERP
que aún está en desarrollo, es la elección obvia.

Dejamos el enum de modalidad en el modelo por si un cliente grande pide *no verifactu* algún día,
pero no lo implementamos ahora.

### Por qué nativo y no delegar en un proveedor (Verifacti, Fiskaly, etc.)

Lo que un proveedor te ahorra es **el transporte**: certificados, SOAP, entornos, reintentos.
Lo que **no** te ahorra:

- El encadenado de registros y la huella son estado de tu base de datos. Tienen que vivir en PERA
  sí o sí, en la misma transacción que la emisión de la factura.
- La declaración responsable sigue siendo tuya: PERA es el SIF, el proveedor es un canal.
- El mapeo `TipoFactura`, `ClaveRegimen`, `CalificacionOperacion`, rectificativas — es lógica de
  negocio del ERP, no del proveedor.

Es decir: delegando te ahorras quizá el 20% del trabajo y te compras una dependencia externa,
un coste por factura y un punto de fallo en el camino crítico de emisión.

**Propuesta concreta:** implementamos todo nativo, pero el envío queda detrás de

```java
public interface VerifactuSubmitter {
    SubmissionResult submit(UUID companyId, List<VerifactuRecord> batch);
}
```

con `AeatSoapSubmitter` como implementación real y `LoggingSubmitter` para desarrollo. Si algún día
el SOAP con certificado se atraganta, se añade un `ThirdPartySubmitter` sin tocar nada más.

---

## 3. Huecos reales de PERA (auditoría del código actual)

Esto es lo que he encontrado revisando `backend/sales-service`. **Ninguno de estos huecos se arregla
mirando el código del jefe** — son decisiones de modelo de datos de PERA.

### 3.1 No existe la factura rectificativa — bloqueante

`document/DocumentType.java`:

```java
public enum DocumentType { QUOTE, SALES_ORDER, DELIVERY_NOTE, INVOICE, WORK_ORDER }
```

Veri*Factu exige clasificar cada factura en `TipoFactura`: `F1` (factura completa), `F2`
(simplificada), `F3` (sustitutiva de simplificadas), `R1`–`R5` (rectificativas por distintos
motivos). Y para las `R*` hace falta `TipoRectificativa` (`S` sustitución / `I` diferencias),
más `FacturasRectificadas` e `ImporteRectificacion`.

Hoy PERA no puede ni representar una rectificativa. Es el primer trabajo, y es trabajo de ERP,
no de Veri*Factu.

### 3.2 El NIF del destinatario existe pero no llega a la factura — bloqueante

`master-data-service/.../party/Party.java` **sí** tiene `tax_id`:

```java
@Column(name = "tax_id", length = 30)
private String taxId;      // nullable
```

pero el snapshot que congela `sales-service` al emitir lo tira:

```java
public record CustomerSnapshot(UUID id, String code, String legalName, boolean active) {}
```

El registro de alta necesita `Destinatarios/IDDestinatario` con **NIF**, o `IDOtro` (código de
país ISO + tipo de identificación 02–07) para no residentes. Tres arreglos:

1. `taxId` pasa a obligatorio para clientes de empresas con Veri*Factu activo (validación, no
   `NOT NULL` a secas: rompería los datos existentes).
2. Añadir `identification_type` y país ISO-3166 alpha-2 a `Party`. Hoy
   `PartyAddress.country` es texto libre de 100 caracteres, que no sirve.
3. Propagar los tres campos a `CustomerSnapshot` y a `commercial_documents`.

### 3.3 El desglose de IVA está a nivel de línea, no de factura

La V6 (`V6__document_tax_snapshots.sql`) congela impuestos por línea, y `TaxCodeSnapshot` solo
lleva `countryCode`, `percentage` y un booleano `exempt`. Veri*Factu quiere un
`Desglose` **agregado**, con un máximo de **12 bloques**, cada uno con:

`Impuesto`, `ClaveRegimen`, `CalificacionOperacion` **o** `OperacionExenta`, `TipoImpositivo`,
`BaseImponible`, `CuotaRepercutida`, y en su caso `TipoRecargoEquivalencia` / `CuotaRecargoEquivalencia`.

Hay que escribir un agregador línea → desglose, y decidir de dónde salen `ClaveRegimen` y
`CalificacionOperacion` (lo natural: configurables en el código de impuesto del maestro, con un
valor por defecto `01` / `S1` en el parámetro de empresa). Un `boolean exempt` no basta: la AEAT
distingue seis causas de exención (E1–E6) y hay que saber cuál es.

### 3.4 Multidivisa

`CommercialDocument` admite `currency != EUR`. Los registros de Veri*Factu van **en euros**.
Buena noticia: ya existen `baseNetAmount` / `baseTaxAmount` / `baseTotalAmount`. Hay que
garantizar que para empresas españolas `baseCurrency = EUR` y usar esos campos, no los nominales.

### 3.5 Inmutabilidad de la factura emitida

`DocumentStatus` es `DRAFT, CONFIRMED, CONVERTED, CANCELLED`. Una vez generado el registro de
alta, la factura **no se puede modificar ni borrar**: se corrige con una rectificativa o se
retira con un `RegistroAnulacion`. Hace falta:

- un estado `ISSUED` (o marcar la emisión con timestamp) a partir del cual `DocumentService`
  rechace cualquier mutación de factura;
- que `CANCELLED` sobre una factura ya emitida dispare anulación, no un cambio de estado a secas.

### 3.6 Certificado por empresa

PERA es multiempresa (`company_id` del JWT en cada agregado). Cada empresa remite con **su**
certificado. `identity-service` ya guarda logos de forma segura; hay que extenderlo a
almacenamiento cifrado de certificados y contraseñas, con rotación y aviso de caducidad.
Esto es un subproyecto pequeño pero real.

### 3.7 Fecha y hora con huso

`FechaHoraHusoGenRegistro` va en ISO 8601 con offset. PERA usa `Instant` y `LocalDate`; hay que
fijar la zona de la empresa como parámetro, no depender de la del servidor.

---

## 4. Arquitectura propuesta

### Dónde vive

**Paquete `verifactu` dentro de `sales-service`. No un microservicio nuevo.**

Razón: la huella de cada registro depende de la huella del anterior, y el registro tiene que
nacer en la **misma transacción** que la emisión de la factura. Un servicio aparte convierte una
operación atómica en un problema de consistencia distribuida sin ganar nada.

```
backend/sales-service/src/main/java/com/peraerp/sales/verifactu/
├── domain/
│   ├── VerifactuRecord.java          // registro de facturación (alta / anulación)
│   ├── VerifactuRecordType.java      // ALTA, ANULACION
│   ├── VerifactuState.java           // PENDING, SENT, ACCEPTED, ACCEPTED_WITH_ERRORS, REJECTED
│   ├── InvoiceChainHead.java         // puntero de cadena por company_id (con bloqueo)
│   └── VerifactuSettings.java        // modalidad, entorno, NIF, clave régimen por defecto
├── hash/
│   ├── RecordFingerprint.java        // SHA-256, hex mayúsculas
│   └── FingerprintInput.java         // construcción de la cadena de entrada
├── mapping/
│   ├── InvoiceToRecordMapper.java    // CommercialDocument -> VerifactuRecord
│   ├── TaxBreakdownAggregator.java   // líneas -> Desglose (máx. 12)
│   └── InvoiceTypeResolver.java      // F1/F2/R1..R5
├── qr/
│   └── VerifactuQrGenerator.java
├── submission/
│   ├── VerifactuSubmitter.java       // interfaz
│   ├── AeatSoapSubmitter.java
│   ├── SubmissionScheduler.java      // lee el outbox, respeta TiempoEsperaEnvio
│   └── SubmissionResult.java
└── api/
    └── VerifactuController.java      // consulta de estado, reenvío manual
```

Reutilizamos el `outbox` que ya existe (`outbox/DomainEventRecorder.java`) para desacoplar
"generar registro" de "enviarlo a la AEAT". La emisión de factura nunca debe fallar porque la
AEAT esté caída.

### El punto delicado: serialización de la cadena

La cadena es **por obligado tributario**, es decir por `company_id`. Dos facturas emitidas a la
vez por la misma empresa no pueden coger la misma huella anterior. Solución mínima y correcta:

```sql
SELECT * FROM verifactu_chain_head WHERE company_id = ? FOR UPDATE;
```

dentro de la transacción de emisión. Serializa las emisiones de una empresa, no las de todas.
Con el volumen de una pyme es perfectamente asumible.

---

## 5. Modelo de datos (migración `V8__verifactu.sql`)

> `issuer_tax_id` / `issuer_legal_name` se duplican a propósito: `identity-service` ya tiene
> `Company.taxId` y `Company.name`, pero si mañana la empresa cambia de razón social los
> registros ya emitidos deben conservar la que tenían. Es el mismo criterio de *snapshot* que
> PERA aplica a clientes y precios. Se rellenan desde `identity-service` al activar el módulo.

```sql
CREATE TABLE verifactu_settings (
    company_id            uuid PRIMARY KEY,
    enabled               boolean      NOT NULL DEFAULT false,
    mode                  varchar(20)  NOT NULL DEFAULT 'VERIFACTU',
    environment           varchar(20)  NOT NULL DEFAULT 'TEST',
    issuer_tax_id         varchar(20)  NOT NULL,   -- copiado de identity Company.taxId
    issuer_legal_name     varchar(180) NOT NULL,
    default_regime_key    varchar(2)   NOT NULL DEFAULT '01',
    default_operation_qualification varchar(2) NOT NULL DEFAULT 'S1',
    time_zone             varchar(60)  NOT NULL DEFAULT 'Europe/Madrid',
    software_name         varchar(120) NOT NULL,
    software_id           varchar(2)   NOT NULL,
    software_version      varchar(50)  NOT NULL,
    developer_tax_id      varchar(20)  NOT NULL
);

CREATE TABLE verifactu_chain_head (
    company_id      uuid PRIMARY KEY,
    last_record_id  uuid,
    last_fingerprint char(64),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE verifactu_records (
    id                    uuid PRIMARY KEY,
    company_id            uuid        NOT NULL,
    document_id           uuid        NOT NULL,
    record_type           varchar(20) NOT NULL,          -- ALTA | ANULACION
    sequence_number       bigint      NOT NULL,
    issuer_tax_id         varchar(20) NOT NULL,
    invoice_number        varchar(60) NOT NULL,
    invoice_date          date        NOT NULL,
    invoice_type          varchar(2)  NOT NULL,          -- F1..R5
    rectification_type    varchar(1),                    -- S | I
    total_tax_amount      numeric(19,4) NOT NULL,
    total_amount          numeric(19,4) NOT NULL,
    previous_fingerprint  char(64),
    fingerprint           char(64)    NOT NULL,
    generated_at          timestamptz NOT NULL,          -- FechaHoraHusoGenRegistro
    payload_xml           text        NOT NULL,          -- registro serializado, inmutable
    state                 varchar(30) NOT NULL,
    aeat_csv              varchar(50),
    aeat_response         text,
    last_attempt_at       timestamptz,
    attempt_count         int         NOT NULL DEFAULT 0,
    CONSTRAINT uk_verifactu_seq UNIQUE (company_id, sequence_number),
    CONSTRAINT uk_verifactu_fingerprint UNIQUE (company_id, fingerprint)
);

CREATE INDEX ix_verifactu_pending ON verifactu_records (company_id, state, generated_at);
```

`payload_xml` se guarda **serializado en el momento de emitir**, no se regenera al enviar.
Si un día cambia el mapeo, los registros antiguos siguen siendo exactamente los que se firmaron.
Es el mismo principio de *snapshot* que PERA ya aplica a clientes, precios e impuestos.

---

## 6. Huella y QR

### Huella — verificada con vector oficial ✅

SHA-256 sobre una cadena `campo=valor&campo=valor…` en UTF-8, salida hexadecimal en mayúsculas
(64 caracteres). El orden de campos lo fija la AEAT y no es alfabético.

*Registro de alta:*

```
IDEmisorFactura=&NumSerieFactura=&FechaExpedicionFactura=&TipoFactura=&CuotaTotal=&ImporteTotal=&Huella=&FechaHoraHusoGenRegistro=
```

*Registro de anulación:*

```
IDEmisorFacturaAnulada=&NumSerieFacturaAnulada=&FechaExpedicionFacturaAnulada=&Huella=&FechaHoraHusoGenRegistro=
```

Reglas: valores recortados de espacios, campo ausente se emite igualmente con valor vacío
(`Huella=` en el primer registro de la cadena), `FechaExpedicionFactura` en **dd-MM-yyyy**,
`FechaHoraHusoGenRegistro` en ISO 8601 con huso y **sin fracciones de segundo**.

**Vector de contraste** (reproducido y fijado en `VerifactuFingerprintTest`):

```
IDEmisorFactura=89890001K&NumSerieFactura=12345678/G33&FechaExpedicionFactura=01-01-2024&TipoFactura=F1&CuotaTotal=12.35&ImporteTotal=123.45&Huella=&FechaHoraHusoGenRegistro=2024-01-01T19:20:30+01:00
```

```
3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60
```

> **Decisión de diseño.** Los importes se emiten siempre con dos decimales y punto decimal
> (`241.40`, no `241.4`). La alternativa —«el número tal cual»— haría que dos representaciones del
> mismo importe produjeran huellas distintas. Por eso el formateo vive en una única clase,
> `VerifactuFieldFormat`, que usarán **tanto la huella como la serialización XML**: si divergen,
> la AEAT rechaza el registro y el error es invisible en revisión de código.

### QR — verificado

- Producción: `https://www2.agenciatributaria.es/wlpl/TIKE-CONT/ValidarQR`
- Pruebas: `https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR`
- Parámetros, en este orden: `nif`, `numserie`, `fecha` (`dd-mm-yyyy`), `importe` (punto decimal).
- Ejemplo: `?nif=89890001K&numserie=12345678/G33&fecha=01-01-2024&importe=241.4`
- ISO/IEC 18004, corrección de errores nivel **M**, impreso entre **30×30 mm y 40×40 mm**,
  margen blanco mínimo 2 mm (recomendado 6 mm).
- Colocado en la parte superior de la factura y **solo en la primera página**.
- Junto al QR, la leyenda **"VERI*FACTU"** cuando se opera en esa modalidad (contrastar el texto
  exacto en el PDF de especificaciones del QR).

Esto toca `frontend/src/lib/document.ts` y el centro de informes imprimibles
(`frontend/src/pages/ReportsPage.tsx`). El QR se puede generar offline: solo depende de datos
que ya están en la factura.

---

## 7. Fases de trabajo

| Fase | Contenido | Depende de | Estado |
|---|---|---|---|
| **0a** | `DocumentType.RECTIFYING_INVOICE`, clasificación F1..R5, criterio S/I e inmutabilidad de la factura expedida | — | ✅ **hecho** (`V7`, 20 pruebas) |
| **0b** | NIF / tipo de identificación / país del destinatario hasta el snapshot de venta | — | ✅ **hecho** (master-data `V7`, sales `V8`, 8 pruebas) |
| **1a** | Modelo de datos `V9__verifactu_records.sql`, entidades, repositorios y encadenado con bloqueo | 0 | ✅ **hecho** (16 pruebas) |
| **1b** | `verifactu_settings` en `SettingsPage` | 1a | ✅ **hecho** (14 pruebas) |
| **2** | Huella y encadenado, con tests contra los vectores oficiales. **Sin red.** | 1 | ✅ **hecho** (`verifactu/hash/`, 13 pruebas) |
| **3a** | Enganche emisión → cadena: la factura expedida genera su registro | 0, 1 | ✅ **hecho** (9 pruebas) |
| **3b** | Desglose de IVA, bloque destinatario, `SistemaInformatico` y serialización XML contra los XSD | 3a | pendiente |
| **4a** | QR de cotejo y bloque Veri*Factu en el detalle de la factura | 3a | ✅ **hecho** — cotejado contra la AEAT en preproducción |
| **4b** | Impresión de factura individual en A4 con QR y leyenda | 4a | pendiente (PERA no imprime facturas todavía) |
| **5** | `AeatSoapSubmitter`: certificado por empresa, WSDL, entorno de pruebas, `TiempoEsperaEnvio`, lotes, reintentos | 3b | pendiente |
| **6** | Anulación y subsanación; pantalla de estado Veri*Factu por factura | 5 | pendiente |
| **7** | Declaración responsable, documentación del producto, `docs/11-ampliacion-plataforma.md` actualizado | todo | pendiente |

Las fases 2 y 3 se pueden hacer enteras sin certificado y sin conexión con la AEAT. Es donde está
la mayor parte del riesgo técnico y es lo que conviene atacar primero.

---

## 8. El proyecto del jefe: qué hay realmente en él

Revisado `vidrioservice-vs-budget` (Angular 8 + Express + Sequelize + MySQL, ficheros de enero
de 2024). **No contiene nada de Veri*Factu.** Cero coincidencias de `verifactu`, `aeat`,
`huella`, `registroalta`, `ticketbai` o `facturae` en todo el repositorio.

Y no es que esté a medias: es que **no es una aplicación de facturación**. Sus modelos son
`budget`, `composition`, `easel`, `container`, `product`, `variant`, `end_customer`… Es el
sustituto del módulo de **presupuestos** de Dimpro, no del de facturación. No hay entidad
factura, ni desglose de IVA, ni numeración fiscal, ni impresión de factura.

Tiene sentido cronológicamente: la Orden HAC/1177/2024, que fija los formatos técnicos, es de
octubre de 2024, nueve meses posterior a estos ficheros.

**Conclusión: para Veri*Factu, aquí no hay nada que copiar.** Si tu jefe ya lo tiene funcionando,
estará en otro sitio:

1. **En DimproCristalWin (Visual FoxPro).** Es lo más probable, porque es lo que está en
   producción emitiendo facturas hoy. Si es ahí, el código en sí no te sirve —portar VFP a
   Java/Spring es reescribir, no portar— pero las **decisiones** valen mucho (ver lista abajo).
2. **En otro repositorio del ERP nuevo** que aún no me has pasado (¿un `vs-invoice`,
   `vs-facturacion` o similar?).
3. **Vía un proveedor externo** contratado, en cuyo caso lo interesante es saber cuál y por qué.

### 8.1 Lo que revela la aplicación desplegada (`dimprocw`)

La implementación real **no está en `vs-budget`**: está en otra aplicación desplegada como
`dimprocw`. De la factura impresa y del listado se deducen estos hechos, sin necesidad de tocar
el sistema:

**Confirmaciones útiles** — coinciden con lo que dice este plan:

- La huella es SHA-256 en **hexadecimal mayúsculas** (`45A297172471AB6744DE8E6D4F2584...`).
- El QR lleva la leyenda **`VERI*FACTU`** debajo y el rótulo **"QR tributario"** al lado.
- El registro se guarda con **UUID propio**, separado del id de la factura.
- Hay un **estado por factura** (`Pendiente`) y un botón de reenvío/actualización en el listado.
  Es exactamente el `VerifactuState` + reintento del plan.
- El bloque Veri*Factu se imprime como sección propia en la factura, con huella y URL de cotejo
  en texto además del QR.

**Tres cosas que hay que preguntarle antes de copiar el enfoque:**

1. **Está apuntando a PREPRODUCCIÓN.** La URL de cotejo es `https://prewww2.aeat.es/...`, no
   `https://www2.agenciatributaria.es/...`. Con `Estado: Pendiente` en todas las facturas, eso
   significa que el sistema **está en pruebas, no emitiendo de verdad**. Así que "mi jefe ya lo
   tiene" hay que matizarlo: lo tiene montado, no lo tiene en producción. No des por validado
   nada de ahí.

2. **`Serie/Número` no coincide con el número de factura.** La factura impresa es
   `F-2026-0000015` pero el registro dice `F01-15`. El `NumSerieFactura` del registro y del QR
   tiene que ser **el número real de la factura expedida**: es la clave con la que el receptor
   coteja. Si son distintos, el cotejo falla. O bien es solo una etiqueta interna mal rotulada
   en la impresión, o es un bug de verdad. Merece la pena preguntarlo.

3. **Formato de `FechaHoraHusoGenRegistro`.** Aparece como `2026-08-03T11:38:11.197Z`: con
   **milisegundos** y en UTC. La AEAT espera ISO 8601 con huso del tipo
   `2026-08-03T11:38:11+02:00`. Los milisegundos son un candidato claro a rechazo por validación.
   Y ojo al dato: la fecha del registro (03/08) es **anterior** a la de expedición de la factura
   (19/08), lo cual no debería poder pasar.

En PERA: `NumSerieFactura` sale de `CommercialDocument.documentNumber` tal cual, y
`FechaHoraHusoGenRegistro` se formatea con la zona de `verifactu_settings.time_zone` y **sin
fracciones de segundo**. Los dos puntos van al test suite de la fase 2.

### Lo que sí merece la pena sacarle, esté donde esté

No pidas el código: pide las **decisiones**. Son las que no vienen en la norma y las que cuesta
meses descubrir a base de rechazos de la AEAT.

1. **Mapeo `TipoFactura`.** ¿Cuándo F1 y cuándo F2? ¿Qué umbral para simplificadas?
2. **Rectificativas.** ¿`S` (sustitución) o `I` (diferencias)? ¿Cómo trata la rectificativa ya
   cobrada — el bug de Cartera que ya conoces?
3. **Exentas y no sujetas.** Qué `CalificacionOperacion` / `OperacionExenta` (E1–E6) asigna a
   exportaciones, intracomunitarias y art. 20.
4. **Recargo de equivalencia.** Cómo lo desglosa si tiene clientes en ese régimen.
5. **Códigos de rechazo de la AEAT** que le han llegado en producción y qué los causó. Esta lista
   es literalmente el catálogo de errores que te vas a comer tú.
6. **Subsanación.** Qué hace cuando la AEAT acepta con errores.
7. **Anulación frente a rectificación.** En qué casos elige cada una.

Con eso construimos la tabla de mapeo de PERA. Si además consigues acceso al fichero de VFP que
construye el registro y calcula la huella, con leerlo una tarde saco los siete puntos.

### Nota aparte, no relacionada con Veri*Factu

`vs-budget` sí es una referencia funcional interesante para el dominio del vidrio
(composiciones, caballetes, contenedores, exportación CSV a optimizador). Pero PERA está
declarado como ERP **horizontal** en `docs/08-generalizacion-erp-horizontal.md`, así que eso
entraría como vertical explícito, no en el núcleo. Tema para otro día.

---

## 9. Decisiones abiertas

1. **¿Facturas simplificadas (tickets)?** Si PERA va a vender a comercio con TPV hay que
   soportar `F2` y `F3` desde el principio; cambia el flujo de emisión.
2. **¿Qué hacemos con los albaranes?** No generan registro. Solo `INVOICE`.
3. **`software_id` y declaración responsable.** Hay que registrar PERA como sistema y decidir
   quién figura como productor (`developer_tax_id`).
4. **Modo de contingencia.** Si la AEAT no responde durante horas, la factura se emite igual y
   el registro queda `PENDING`. Hay que definir el aviso al usuario y el límite de reintentos.
5. **Clientes en País Vasco o Navarra.** TicketBAI sustituye a Veri*Factu allí. Si algún cliente
   potencial está en esas provincias, es un módulo aparte.

---

## Fuentes

- [AEAT — Sistemas informáticos de facturación y Veri*Factu (FAQ)](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/preguntas-frecuentes.html)
- [AEAT — Información técnica (XSD, WSDL, huella, QR, validaciones y errores)](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html)
- [AEAT — Algoritmo de cálculo de la huella o «hash»](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica/algoritmo-calculo-codificacion-huella-hash.html)
- [AEAT — Características del QR y servicio de cotejo](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica/caracteristicas-qr-especificaciones-servicio-cotejo-factura.html)
- [AEAT — FAQs para desarrolladores (PDF)](https://sede.agenciatributaria.gob.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/FAQs-Desarrolladores.pdf)
- [Portal de pruebas externas de la AEAT](https://preportal.aeat.es)
- [Especificación técnica del código QR Veri*Factu — CodigoNext](https://www.codigonext.com/recursos/verifactu-codigo-qr/)
- [Nuevos plazos de entrada en vigor tras el RDL 15/2025 — Muaytax](https://muaytax.com/es/verifactu-entrada-en-vigor/)
