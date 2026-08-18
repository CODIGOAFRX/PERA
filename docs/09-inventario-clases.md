# Inventario de clases auditadas

Inventario del estado revisado el 7 de agosto de 2026. Cada uno de los 164 archivos Java aparece una vez. Estados:

- **Activa**: pertenece al núcleo horizontal o a una capacidad transversal opcional.
- **Activa/compatibilidad**: la clase es horizontal, pero contiene un campo heredado congelado.
- **Congelada**: se conserva sin borrar, pero no recibirá nuevas dependencias ni funcionalidad.
- **Prueba**: verifica una regla transversal.

## Plataforma compartida

| Clase | Responsabilidad | Estado |
|---|---|---|
| `AuditableEntity` | UUID, fechas de auditoría y versión optimista comunes. | Activa |
| `AuthenticationFailedException` | Error de autenticación de dominio. | Activa |
| `BusinessRuleException` | Error uniforme para reglas de negocio. | Activa |
| `CompanyScopedEntity` | Añade `companyId` a agregados multiempresa. | Activa |
| `ResourceNotFoundException` | Error uniforme de recurso inexistente. | Activa |
| `JwtPermissionGrantedAuthoritiesConverter` | Convierte permisos JWT en autoridades Spring. | Activa |
| `ApiExceptionHandler` | Traduce excepciones a respuestas `ProblemDetail`. | Activa |

## API Gateway

| Clase | Responsabilidad | Estado |
|---|---|---|
| `ApiGatewayApplication` | Arranque del gateway reactivo. | Activa |
| `SecurityConfig` | Seguridad JWT, CORS y autorización de rutas. | Activa |

## Identidad — acceso

| Clase | Responsabilidad | Estado |
|---|---|---|
| `Permission` | Permiso atómico del sistema. | Activa |
| `PermissionRepository` | Persistencia y búsqueda de permisos. | Activa |
| `Role` | Rol de empresa y conjunto de permisos. | Activa |
| `RoleRepository` | Persistencia de roles por empresa. | Activa |
| `UserCompany` | Pertenencia de un usuario a una empresa y sus roles. | Activa |
| `UserCompanyRepository` | Consulta pertenencias por usuario y empresa. | Activa |

## Identidad — autenticación

| Clase | Responsabilidad | Estado |
|---|---|---|
| `AuthController` | Expone el inicio de sesión. | Activa |
| `AuthService` | Valida credenciales, empresa activa y pertenencia. | Activa |
| `CompanyOption` | Opción de empresa cuando el usuario debe seleccionarla. | Activa |
| `JwtService` | Emite JWT con empresa, roles y permisos. | Activa |
| `LoginRequest` | Contrato validado de login. | Activa |
| `LoginResponse` | Token o solicitud de selección de empresa. | Activa |

## Identidad — empresas

| Clase | Responsabilidad | Estado |
|---|---|---|
| `Company` | Empresa operativa del tenant. | Activa |
| `CompanyController` | API de consulta, alta y actualización de empresas. | Activa |
| `CompanyRepository` | Persistencia y búsquedas de empresas. | Activa |
| `CompanyRequest` | Contrato validado de empresa. | Activa |
| `CompanyResponse` | Representación REST de empresa. | Activa |
| `CompanyService` | Reglas y transacciones de empresas. | Activa |

## Identidad — configuración y usuarios

| Clase | Responsabilidad | Estado |
|---|---|---|
| `BootstrapDataInitializer` | Sincroniza permisos, cinco perfiles base y sus usuarios de demostración. | Activa |
| `JwtProperties` | Configuración tipada del emisor JWT. | Activa |
| `SecurityConfig` | Autenticación, hash de contraseña y permisos HTTP. | Activa |
| `IdentityServiceApplication` | Arranque del servicio de identidad. | Activa |
| `AppUser` | Usuario y credenciales cifradas. | Activa |
| `AppUserRepository` | Persistencia y búsqueda de usuarios. | Activa |
| `CreateUserRequest` | Contrato para crear usuario y pertenencia. | Activa |
| `UpdateUserRequest` | Contrato para editar perfil, contraseña, roles y estado. | Activa |
| `RoleCatalogController`, `RoleCatalogService`, `RoleResponse` | Catálogo asignable de perfiles y permisos del tenant. | Activa |
| `UserAdministrationController` | API de listado, alta y edición de usuarios. | Activa |
| `UserAdministrationService` | Administración tenant-scoped y protección del perfil propietario. | Activa |
| `UserResponse` | Representación segura del usuario, sin contraseña. | Activa |

## Maestros — catálogo y precios

| Clase | Responsabilidad | Estado |
|---|---|---|
| `PriceList` | Cabecera de lista de precios por empresa y moneda. | Activa |
| `PriceListItem` | Precio de producto con periodo de vigencia. | Activa |
| `PriceListItemRepository` | Persistencia de precios de lista. | Activa |
| `PriceListRepository` | Persistencia de listas de precios. | Activa |
| `Product` | Artículo o servicio vendible con precio e impuesto base. | Activa |
| `ProductCategory` | Clasificación configurable de productos. | Activa |
| `ProductCategoryRepository` | Persistencia de categorías. | Activa |
| `ProductController` | API de consulta, alta y actualización de productos. | Activa |
| `ProductFamily` | Familia configurable de productos. | Activa |
| `ProductFamilyRepository` | Persistencia de familias. | Activa |
| `ProductRepository` | Búsqueda multiempresa de productos. | Activa |
| `ProductRequest` | Contrato validado de producto. | Activa |
| `ProductResponse` | Representación REST de producto. | Activa |
| `ProductService` | Reglas y transacciones del catálogo. | Activa |
| `ProductType` | Tipo configurable de producto o servicio. | Activa |
| `ProductTypeRepository` | Persistencia de tipos. | Activa |
| `UnitOfMeasure` | Unidades generales: unidad, longitud, superficie, volumen, peso, líquido y tiempo. | Activa |

## Maestros — clientes

| Clase | Responsabilidad | Estado |
|---|---|---|
| `CustomerController` | API de consulta y alta de clientes. | Activa |
| `CustomerNote` | Nota interna opcional vinculada a cliente. | Activa |
| `CustomerNoteRepository` | Persistencia de notas de cliente. | Activa |
| `CustomerProfile` | Perfil comercial, pago y riesgo del cliente; conserva el multiplicador legado. | Activa/compatibilidad |
| `CustomerProfileRepository` | Persistencia y búsqueda de perfiles de cliente. | Activa |
| `CustomerRequest` | Contrato de cliente; acepta el multiplicador solo por compatibilidad. | Activa/compatibilidad |
| `CustomerResponse` | Respuesta de cliente; devuelve el multiplicador marcado obsoleto. | Activa/compatibilidad |
| `CustomerService` | Alta y consulta de cliente; actúa como frontera de persistencia del valor legado. | Activa/compatibilidad |
| `CustomerSpecialRate` | Ajuste tipado de precio por cliente/producto y vigencia. | Activa |
| `CustomerSpecialRateRepository` | Persistencia de ajustes de precio activos. | Activa |
| `CustomerSpecificPrice` | Precio pactado por cliente/producto y vigencia. | Activa |
| `CustomerSpecificPriceRepository` | Persistencia de precios específicos. | Activa |
| `PriceAdjustmentType` | Tipos explícitos: descuento, recargo o margen fijo. | Activa |
| `RiskPolicy` | Política comercial ante exposición de crédito. | Activa |
| `WorkSite` | Antiguo maestro de obra con campo de constructor. | Congelada |
| `WorkSiteRepository` | Acceso al maestro heredado de obras. | Congelada |

## Maestros — terceros y proveedores

| Clase | Responsabilidad | Estado |
|---|---|---|
| `Party` | Identidad fiscal y comercial común de un tercero. | Activa |
| `PartyAddress` | Dirección tipada de un tercero. | Activa |
| `PartyAddressRepository` | Persistencia de direcciones. | Activa |
| `PartyContact` | Persona y canales de contacto de un tercero. | Activa |
| `PartyContactRepository` | Persistencia de contactos. | Activa |
| `PartyRepository` | Persistencia y búsquedas de terceros. | Activa |
| `SupplierController` | API de consulta y alta de proveedores. | Activa |
| `SupplierProfile` | Perfil de pago y logística opcional de proveedor. | Activa |
| `SupplierProfileRepository` | Persistencia y búsqueda de proveedores. | Activa |
| `SupplierRequest` | Contrato validado de proveedor. | Activa |
| `SupplierResponse` | Representación REST de proveedor. | Activa |
| `SupplierService` | Reglas y transacciones de proveedores. | Activa |

## Maestros — configuración

| Clase | Responsabilidad | Estado |
|---|---|---|
| `CurrentCompanyProvider` | Obtiene la empresa activa del JWT. | Activa |
| `SecurityConfig` | Autoriza las APIs de maestros por permisos. | Activa |
| `MasterDataServiceApplication` | Arranque del servicio de maestros. | Activa |

## Ventas

| Clase | Responsabilidad | Estado |
|---|---|---|
| `CurrentCompanyProvider` | Obtiene la empresa activa del JWT. | Activa |
| `SecurityConfig` | Autoriza documentos por permisos de lectura/escritura. | Activa |
| `CommercialDocument` | Agregado de presupuesto, albarán, factura o parte. | Activa |
| `CommercialDocumentRepository` | Persistencia y filtros de documentos comerciales. | Activa |
| `SalesDashboardController`, `SalesDashboardService` | Agrega facturación mensual, acumulados diarios y ritmo esperado. | Activa |
| `SalesDashboardResponse`, `MonthlyRevenuePoint`, `DailyRevenuePoint`, `InvoiceRevenueEntry` | Contratos inmutables del dashboard económico. | Activa |
| `CreateDocumentRequest` | Contrato validado de creación documental. | Activa |
| `DocumentAmountsCalculator` | Calcula neto, impuesto y total con `BigDecimal`. | Activa |
| `DocumentController` | API de consulta, creación, conversión y cobro. | Activa |
| `DocumentLine` | Línea con snapshots de producto, precio e impuesto. | Activa |
| `DocumentLineRequest` | Contrato validado de línea. | Activa |
| `DocumentLineResponse` | Representación calculada de línea. | Activa |
| `DocumentNumberGenerator` | Genera numeración por tipo, empresa y ejercicio. | Activa |
| `DocumentResponse` | Representación completa de documento y líneas. | Activa |
| `DocumentSequence` | Contador transaccional de numeración. | Activa |
| `DocumentSequenceRepository` | Bloquea y persiste secuencias. | Activa |
| `DocumentService` | Crea, consulta, convierte y actualiza documentos. | Activa |
| `DocumentStatus` | Estados del ciclo documental. | Activa |
| `DocumentType` | Presupuesto, albarán, factura y parte de trabajo. | Activa |
| `LineAmounts` | Resultado inmutable del cálculo de una línea. | Activa |
| `PaymentStatus` | Estado de cobro resumido de factura. | Activa |
| `UpdatePaymentStatusRequest` | Contrato para cambiar el estado de cobro. | Activa |
| `DomainEventRecorder` | Serializa y registra eventos en outbox. | Activa |
| `OutboxEvent` | Evento transaccional pendiente de publicación. | Activa |
| `OutboxEventRepository` | Persistencia del outbox. | Activa |
| `SalesServiceApplication` | Arranque del servicio de ventas. | Activa |
| `DocumentAmountsCalculatorTest` | Verifica descuento, impuesto y redondeo monetario. | Prueba |

## Finanzas — pagos y vencimientos

| Clase | Responsabilidad | Estado |
|---|---|---|
| `PaymentMethod` | Forma de pago con reglas de vencimiento. | Activa |
| `PaymentMethodController` | API de consulta y alta de formas de pago. | Activa |
| `PaymentMethodRepository` | Persistencia de formas de pago por empresa. | Activa |
| `PaymentMethodRequest` | Contrato validado de forma de pago y reglas. | Activa |
| `PaymentMethodResponse` | Representación REST de forma de pago. | Activa |
| `PaymentMethodService` | Valida porcentajes y administra formas de pago. | Activa |
| `PaymentRuleRequest` | Días y porcentaje de un plazo solicitado. | Activa |
| `PaymentRuleResponse` | Días y porcentaje de un plazo devuelto. | Activa |
| `PaymentScheduleRule` | Regla persistida de vencimiento. | Activa |
| `DocumentDueDate` | Plazo pendiente, parcial, pagado o cancelado. | Activa |
| `DocumentDueDateRepository` | Persistencia y consulta de vencimientos. | Activa |
| `DueDateController` | API de generación y consulta de vencimientos. | Activa |
| `DueDateResponse` | Representación REST de vencimiento. | Activa |
| `DueDateService` | Genera vencimientos idempotentes por documento. | Activa |
| `DueDateStatus` | Estados de un vencimiento. | Activa |
| `GenerateDueDatesRequest` | Contrato de generación de vencimientos. | Activa |
| `PaymentScheduleCalculator` | Reparte importe y resto de redondeo entre plazos. | Activa |
| `ScheduleItem` | Resultado inmutable de un plazo calculado. | Activa |
| `PaymentScheduleCalculatorTest` | Verifica reparto, fechas y resto de redondeo. | Prueba |

## Finanzas — cobros, remesas y mayor

| Clase | Responsabilidad | Estado |
|---|---|---|
| `Receipt` | Derecho de cobro asociado a cliente y documento. | Activa |
| `ReceiptRepository` | Persistencia de recibos. | Activa |
| `ReceiptStatus` | Estados del ciclo de cobro/remesa. | Activa |
| `Remittance` | Agrupación bancaria de recibos. | Activa |
| `RemittanceReceipt` | Relación entre remesa y recibo. | Activa |
| `RemittanceReceiptRepository` | Persistencia de relaciones remesa-recibo. | Activa |
| `RemittanceRepository` | Persistencia de remesas. | Activa |
| `RemittanceStatus` | Estados de una remesa. | Activa |
| `FinancialMovement` | Apunte financiero de débito/crédito con referencias. | Activa |
| `FinancialMovementRepository` | Persistencia de movimientos financieros. | Activa |
| `FinancialMovementType` | Origen o naturaleza del movimiento financiero. | Activa |

## Finanzas — riesgo y caja

| Clase | Responsabilidad | Estado |
|---|---|---|
| `CustomerRisk` | Exposición, límites y acción comercial del cliente. | Activa |
| `CustomerRiskRepository` | Persistencia del riesgo por cliente y empresa. | Activa |
| `RiskAction` | Advertir, pedir confirmación o bloquear. | Activa |
| `CashMovement` | Movimiento ocurrido dentro de una sesión de caja. | Activa |
| `CashMovementRepository` | Persistencia de movimientos de caja. | Activa |
| `CashMovementType` | Apertura, cobro, ingreso, gasto, retirada o ajuste. | Activa |
| `CashRegister` | Caja física o lógica configurable. | Activa |
| `CashRegisterRepository` | Persistencia de cajas. | Activa |
| `CashSession` | Apertura, cierre e importes esperados/reales. | Activa |
| `CashSessionRepository` | Persistencia de sesiones de caja. | Activa |
| `CashSessionStatus` | Estado abierto o cerrado de sesión. | Activa |

## Finanzas — configuración

| Clase | Responsabilidad | Estado |
|---|---|---|
| `CurrentCompanyProvider` | Obtiene la empresa activa del JWT. | Activa |
| `SecurityConfig` | Autoriza pagos y vencimientos por permisos. | Activa |
| `FinanceServiceApplication` | Arranque del servicio financiero. | Activa |

## Pruebas añadidas para el MVP frontend

| Clase | Responsabilidad | Estado |
|---|---|---|
| `ApiExceptionHandlerTest` | Verifica errores 400, 401, 404, 422 y 500 sin filtrar detalles internos. | Prueba |
| `AuthServiceTest` | Verifica credenciales, selección de empresa y emisión de sesión. | Prueba |
| `CompanyServiceTest` | Verifica alta, actualización y códigos únicos de empresa. | Prueba |
| `CustomerServiceTest` | Verifica alta, edición, aislamiento y paginación segura de clientes. | Prueba |
| `SupplierServiceTest` | Verifica alta, edición y paginación segura de proveedores. | Prueba |
| `ProductServiceTest` | Verifica alta, edición e inmutabilidad del código de producto. | Prueba |
| `DocumentNumberGeneratorTest` | Verifica secuencias documentales concurrentes por empresa y tipo. | Prueba |
| `DocumentServiceTest` | Verifica creación, conversión y reglas del estado de cobro. | Prueba |
| `PaymentMethodServiceTest` | Verifica que los plazos sumen exactamente el 100 %. | Prueba |
| `DueDateServiceTest` | Verifica generación idempotente y aislamiento de vencimientos. | Prueba |

## Resumen

Las únicas clases completas congeladas son `WorkSite` y `WorkSiteRepository`. `CustomerProfile`, `CustomerRequest`, `CustomerResponse` y `CustomerService` permanecen activas porque su responsabilidad principal es horizontal; únicamente mantienen la ruta de compatibilidad de `calculationMultiplier`. El resto de clases se conserva como núcleo o capacidad transversal opcional.
