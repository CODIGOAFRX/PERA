# Auditoría de generalización a ERP horizontal

Fecha de decisión: 6 de agosto de 2026
Ámbito auditado: backend, migraciones, configuración, infraestructura y documentación de PERA ERP.

## Resultado ejecutivo

PERA puede continuar como ERP horizontal sin rehacer la arquitectura existente. La auditoría no ha encontrado clases, endpoints ni reglas de negocio que modelen específicamente cristales: no existen espesores, planchas, cortes, canteados, templados, laminados, fórmulas por ancho/alto o cálculos automáticos por metro cuadrado.

El núcleo actual está formado por capacidades transversales de identidad, terceros, catálogo, ventas, cobros y tesorería. Solo se han identificado dos restos heredados que no deben seguir evolucionando dentro del núcleo:

1. `WorkSite` y `WorkSiteRepository`, por usar el concepto de obra y el campo `builder`, propios de una extensión orientada a construcción.
2. `calculationMultiplier` de `CustomerProfile` y su columna `calculation_multiplier`, porque carece de una semántica validada y podría introducir reglas de precio opacas.

No se ha borrado código, tabla, columna ni migración. Ambos elementos se conservan para compatibilidad, quedan marcados como obsoletos y no son dependencias de ventas, finanzas, gateway ni identidad.

## Evidencia y alcance

Sobre el estado base se revisaron 190 archivos versionados: 154 archivos Java, cuatro migraciones Flyway, el script SQL de inicialización, siete configuraciones YAML, los POM, Dockerfile, Compose y toda la documentación Markdown. El inventario nominal está en [09-inventario-clases.md](09-inventario-clases.md).

Se buscaron además referencias cruzadas a terminología de cristalería, dimensiones, tratamientos y procesos de fabricación. Fuera del PDF histórico no apareció ningún modelo de ese tipo.

## Clasificación funcional

| Área | Decisión | Motivo |
|---|---|---|
| Plataforma compartida y gateway | Mantener activa | Seguridad, errores, tenant y enrutamiento son transversales. |
| Identidad | Mantener activa | Empresas, usuarios, roles, permisos y JWT son núcleo de cualquier ERP. |
| Terceros | Mantener activa | Clientes, proveedores, direcciones y contactos son maestros horizontales. |
| Catálogo y tarifas | Mantener activa | Productos, familias, categorías, unidades y listas de precios son configurables. |
| Precios por cliente | Mantener activa | Los precios específicos y ajustes tipados sirven al B2B en muchos sectores. |
| Ventas | Mantener activa | Presupuesto, albarán, factura y su conversión son flujo comercial estándar. |
| Parte de trabajo | Mantener activo, opcional | Es aplicable a mantenimiento, instalaciones y empresas de servicios, no solo a cristalería. |
| Finanzas | Mantener activa | Formas de pago, vencimientos, recibos, remesas, riesgo y caja son transversales. |
| Datos logísticos de proveedor | Mantener activos, opcionales | `carrier` y `route` son útiles en distribución mayorista de cualquier producto. |
| `WorkSite` | Congelar | El lenguaje de obra/constructor no es suficientemente horizontal y no tiene API activa. |
| `calculationMultiplier` | Congelar | Es un parámetro opaco y no participa en ningún cálculo implementado. |

## Elementos que podrían parecer sectoriales, pero se mantienen

- `UnitOfMeasure.SQUARE_METER`, `METER` y `CUBIC_METER`: son unidades generales para tejidos, pintura, madera, construcción, transporte, alquileres y muchos otros negocios. No contienen fórmulas de cristalería.
- `CustomerSpecialRate` y `CustomerSpecificPrice`: representan descuento, recargo, margen fijo o precio pactado con vigencia. La semántica es explícita y común en venta B2B.
- `supplierCode`: permite guardar el código que un cliente usa para identificar a nuestra empresa como proveedor. Es una necesidad habitual de intercambio B2B.
- `SupplierProfile.carrier` y `route`: son metadatos opcionales de aprovisionamiento o reparto, válidos para cualquier mayorista.
- `DocumentType.WORK_ORDER`: un parte de trabajo es útil en reparación, mantenimiento, asistencia e instalación. No depende de una obra ni de productos de vidrio.
- Logística comercial implícita en albaranes: se conserva porque distribución y servicios con entrega también la necesitan.

## Comprobación de dependencias

### Modelo de obra

Las únicas referencias de producción a `WorkSite` son la propia entidad y `WorkSiteRepository`. La tabla `work_sites` solo aparece en su mapeo y migración. No existen controlador, servicio, ruta de gateway, DTO, evento ni referencia desde otro agregado. Por tanto, congelarlo no rompe el flujo ejecutable.

### Multiplicador heredado

`calculationMultiplier` recorre únicamente la frontera de compatibilidad de clientes:

```text
CustomerRequest -> CustomerService -> CustomerProfile -> CustomerResponse
```

No lo consume `ProductService`, `DocumentAmountsCalculator`, `DocumentLine`, `DocumentService`, tarifas, finanzas ni el outbox. El cálculo comercial usa exclusivamente cantidad, precio unitario, descuento e impuesto. El valor se almacena y devuelve, pero no altera importes.

### Servicios

No hay imports Java entre el paquete de obra y ventas/finanzas. Las referencias entre microservicios son UUID y snapshots de cliente o producto; esto permite mantener extensiones sectoriales fuera del núcleo.

## Cambios aplicados

- `WorkSite` y `WorkSiteRepository` se han anotado con `@Deprecated(forRemoval = false)` y documentación que prohíbe nuevas dependencias del núcleo.
- El campo y getter `calculationMultiplier` se han marcado como obsoletos en `CustomerProfile`.
- Los componentes equivalentes de `CustomerRequest` y `CustomerResponse` se conservan por compatibilidad y aparecen como obsoletos en OpenAPI.
- La migración aditiva `V2__document_frozen_legacy_fields.sql` añade comentarios de catálogo a PostgreSQL; no modifica ni elimina datos.
- README, visión, requisitos, arquitectura, modelo de datos, API, decisiones y roadmap ya describen PERA como ERP horizontal.

## Desarrollo congelado

Hasta que exista una decisión de producto distinta, no se crearán CRUD, endpoints, eventos, pantallas ni relaciones nuevas alrededor de:

- `WorkSite` / `WorkSiteRepository` / `work_sites`.
- `CustomerProfile.calculationMultiplier` / `calculation_multiplier`.

Congelado no significa eliminado: se compila, se valida con Hibernate y se conserva en PostgreSQL. Si en el futuro se recupera una vertical de construcción o cristalería, deberá reactivarse como extensión separada y no como requisito del núcleo.

## Reglas para nuevas funcionalidades

1. Usar lenguaje transversal en entidades y contratos. Por ejemplo, `project` o `serviceLocation` solo después de validar el concepto, no renombrar automáticamente una obra heredada.
2. No introducir cálculos mediante multiplicadores sin nombre. Toda regla debe indicar base, prioridad, vigencia, redondeo y efecto —descuento, recargo, margen o precio fijo—.
3. Mantener las verticales en módulos opcionales y comunicar con el núcleo mediante IDs, eventos o puertos estables.
4. Ningún servicio del núcleo puede importar clases de un módulo vertical.
5. Las migraciones de retirada serán una decisión posterior, explícita y acompañada de migración de datos; esta auditoría no autoriza borrados.

## Próximo foco recomendado

El esqueleto todavía no es un ERP listo para producción. El siguiente incremento horizontal debería priorizar pruebas de aislamiento multiempresa, CRUD completos de maestros, compras e inventario básico, validación entre ventas y maestros, ciclo fiscal de facturas y automatización fiable entre ventas y finanzas. Las extensiones sectoriales quedan detrás de esas capacidades comunes.
