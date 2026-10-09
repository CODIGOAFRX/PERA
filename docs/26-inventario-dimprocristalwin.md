# 26 — Inventario de DimproCristalWin frente a PERA

Fecha de corte: 9 de octubre de 2026

Inventario opción por opción del menú principal de DimproCristalWin (`MENUS\PRINCIPAL.MPR`), con lo que abre cada una, el tamaño de su código y su estado en PERA. Es la base para traer a PERA todo lo que hace el programa anterior: se copia **lo que hace** (opciones, campos, reglas y cálculos, comprobados con datos reales), no la estructura de su código, que es de formularios de Visual FoxPro sobre ficheros DBF y arrastra fallos conocidos.

Se ha generado leyendo el código fuente: el menú, los formularios (`FORMS\*.SCT`) y los programas (`PROGS\*.prg`). Afina y sustituye al [mapa del doc 19](19-mapa-migracion-dimprocristalwin.md), que iba por áreas.

## Resumen

150 opciones de menú en 13 menús.

| Estado | Opciones | Qué significa |
|---|---|---|
| Hecho | 36 (24 %) | PERA ya lo hace. |
| Parcial | 41 (27 %) | PERA lo hace en parte; falta lo que dice la nota. |
| Pendiente | 29 (19 %) | Falta en PERA y es del núcleo común. |
| Extensión vidrio | 20 (13 %) | Propio del vidrio: va en la extensión de vidrio (bloque 7). |
| Revisar | 16 (11 %) | Hay que abrir la pantalla y ver qué hace antes de decidir. |
| No aplica | 8 (5 %) | Mantenimiento propio de Visual FoxPro o de cómo guarda los datos; PERA no lo necesita. |

El tamaño es orientativo: son las líneas del formulario o programa, incluidas las propiedades de los controles. Sirve para comparar, no como medida exacta del código.

Lo más grande no está en el menú sino en las pantallas a las que llaman, sobre todo el formulario de documentos y el de líneas de documento. Ahí está el corazón del programa (ver «Pantallas internas»).

## Archivo

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Empresa | `form:empresa` | 15790 | Parcial | Configuración › Empresa. El formulario del legado guarda muchos parámetros de funcionamiento que hay que repasar uno a uno. |
| Usuarios › Grupo Usuarios | `form:grupos_usuarios` | 1336 | Parcial | Perfiles fijos (propietario, administración, economía, logística, catálogo). Sin grupos con permisos por opción a medida. |
| Usuarios › Usuarios | `form:usuarios` | 7888 | Hecho | /usuarios |
| Tarifas › Grupo de Tarifas | `form:grupo_tarifas` | 581 | Parcial | Tarifas con tarifa madre en /maestros; sin grupos de tarifas como tal. |
| Tarifas › Tarifas | `form:tipo_tarifas` | 2307 | Hecho | /maestros › Tarifas |
| Articulos › Clase de Artículo | `form:clases_de_articulo` | 1013 | Hecho | Clasificación de productos (naturaleza, supertipo, tipo, grupo) en /maestros |
| Articulos › Familia/Sub-familias de Artículos | `form:grupos_articulos` | 2178 | Hecho | Familias y categorías de producto |
| Articulos › Cámaras Doble Acristalamiento | `form:camaras` | 1052 | Extensión vidrio | Bloque 7 |
| Articulos › Propiedades Generales | `form:propiedades_generales` | 3106 | Revisar | Hay que ver qué guarda antes de decidir. |
| Articulos › Recargos Generales | `prg:recargos_generales` | 3 | Extensión vidrio | Recargos por medidas del vidrio. Bloque 7. |
| Articulos › Catalogos Figuras | `form:catalogo_figuras` | 6036 | Extensión vidrio | Bloque 7 |
| Articulos › Agrupación | `form:agrupacion` | 661 | Revisar |  |
| Articulos › Tipo de Demora | `form:tipo_demora` | 1113 | Revisar |  |
| Articulos › Tipo de Artículos | `form:tipo_de_articulo` | 9555 | Parcial | Tipos de producto en /maestros. El legado calcula precios por tipo; falta revisarlo. |
| Articulos › Tipo de Fabricación | `form:tipo_fabricacion` | 1325 | Extensión vidrio | Bloque 7 |
| Articulos › Tipos de Láminas-Pinturas | `form:tipos_laminas_pinturas` | 1126 | Extensión vidrio | Bloque 7 |
| Articulos › Almacén | `form:malmacen` | 1454 | Hecho | /almacen › Almacenes |
| Articulos › Artículo | `form:articulos_en_general` | 3241 | Parcial | /catalogo. La ficha del legado es mucho más amplia (propiedades de vidrio, costes, compras). |
| Articulos › Diario Almacén | `form:diario_almacen` | 2976 | Hecho | /almacen › Diario |
| Articulos › Secciones Fabricación | `form:secciones_fabricacion` | 2648 | Pendiente | Producción |
| Agenda Telefónica | `form:listin_telefonico` | 1962 | Hecho | /agenda › Listín |
| Clientes › Códigos Postales | `form:codigos_postales` | 1416 | Pendiente | Autocompletar población y provincia. |
| Clientes › Formas de Pago | `form:forma_pago` | 3686 | Hecho | Formas de pago en Finanzas |
| Clientes › Vendedores | `form:vendedores` | 10985 | Hecho | /clientes › Comerciales y /comisiones |
| Clientes › Cobradores | `form:cobradores` | 975 | Pendiente |  |
| Clientes › Grupos de Clientes | `form:grupo_clientes` | 1102 | Hecho | /clientes › Clasificación |
| Clientes › Asunto Cartas/Documentos | `form:asunto_cartas` | 701 | Pendiente | Junto a plantillas de documento (bloque 6). |
| Clientes › Tipos Clientes C.C. | `form:tipos_cc` | 764 | Pendiente | Crédito y caución. |
| Clientes › Rutas › Transportes | `form:rutas_clientes` | 4169 | Parcial | Rutas de reparto en /operaciones; sin asignar ruta por cliente y día. |
| Clientes › Rutas › Comerciales | `form:rutas_comerciales` | 3320 | Pendiente | Rutas de visita de los comerciales. |
| Clientes › Forma Entrega | `form:forma_entrega` | 1801 | Hecho | /clientes › Clasificación |
| Clientes › Datos de Clientes | `prg:clientes` | 5 | Hecho | /clientes |
| Clientes › Actualiza Riesgo Clientes | `form:actualiza_riesgo_clientes` | 1078 | Parcial | El riesgo se calcula al vender; sin recálculo masivo. |
| Clientes › Motivos Inactivo | `form:motivos_inactivos` | 749 | Hecho | /clientes › Clasificación |
| Clientes › Obras clientes | `form:mobras_clientes` | 7834 | Parcial | Direcciones de entrega del cliente; la obra como tal está congelada. |
| Proveedores | `prg:proveedores` | 8 | Hecho | /proveedores |
| Tarifas Especiales › Tarifas Especiales | `form:mtarifas_especiales` | 3080 | Hecho | Precios y reglas por cliente en /maestros |
| Tarifas Especiales › Desglose Tarifas Especiales | `form:tarifas_especiales` | 4237 | Hecho | Precios y reglas por cliente en /maestros |
| Bancos › Bancos | `form:bancos` | 1161 | Revisar | Entidades bancarias; en PERA el banco va como IBAN en cada ficha. |
| Bancos › Agencias | `form:sucursales_bancos` | 1332 | Revisar | Sucursales; el IBAN las hace innecesarias. Confirmar. |
| Tipo de Documentos › Estado de los Documentos | `form:estados_documentos` | 889 | Parcial | Estados fijos (borrador, confirmado, convertido, anulado). |
| Tipo de Documentos › Tipo Factura. (Calendario facturas) | `form:tipo_facturas` | 473 | Pendiente | Facturación automática (bloque 3). |
| Diario de Caja › Cuentas Contables | `form:cuentas_contables` | 871 | Parcial | /contabilidad; revisar si el plan de cuentas cubre lo del legado. |
| Diario de Caja › Claves Diario de caja | `form:claves_caja` | 873 | Parcial | Movimientos de caja en /cartera con concepto libre. |
| Diario de Caja › Origen Apuntes Caja | `form:origen_apuntes_caja` | 968 | Parcial | Movimientos de caja en /cartera. |
| Diario de Caja › Diario de caja | `form:diario_de_caja` | 8746 | Hecho | /cartera › Caja |
| Grupo Descuento | `form:grupo_descuento` | 1789 | Revisar |  |
| Monedas | `form:monedas` | 1431 | Hecho | Configuración › Divisas |
| Calendario Facturación | `form:calendario_facturacion` | 2782 | Pendiente | Facturación automática (bloque 3). |
| Series de Documentos › Agrupación para listados de Series de Documentos | `form:agrupaciones_fseries` | 669 | Pendiente |  |
| Series de Documentos › Grupo Serie Documentos | `form:grupo_fseries` | 721 | Pendiente |  |
| Series de Documentos › Estados Documentos | `form:estados_documentos` | 889 | Parcial | Estados fijos. |
| Series de Documentos › Series de Documentos | `form:series_facturas` | 9279 | Hecho | Configuración › Numeraciones |
| Series de Documentos › Clasificación Documentos | `form:tipos_iso` | 530 | Pendiente | Clasificación ISO de documentos. |
| Medidor/Montador | `form:medidores` | 659 | Extensión vidrio | Mediciones y montajes. Bloque 7. |
| Formas - Figuras › Empresa Figuras | `form:empresa_figuras` | 610 | Extensión vidrio | Bloque 7 |
| Formas - Figuras › Grupo Formas | `form:grupo_formas` | 909 | Extensión vidrio | Bloque 7 |
| Formas - Figuras › Catálogo Figuras | `form:catalogo_figuras` | 6036 | Extensión vidrio | Bloque 7 |
| Formas - Figuras › Formas | `form:formas` | 795 | Extensión vidrio | Bloque 7 |
| Tipo Trabajos | `form:tipo_trabajos` | 944 | Revisar |  |
| Días Entrega por Metros | `form:metros_dias_entrega` | 730 | Extensión vidrio | Bloque 7 |
| Operarios/Elementos | `form:operarios` | 4411 | Pendiente | Producción |
| Control Caballetes › Tipo Caballete | `form:tipo_Caballete` | 850 | Extensión vidrio | Bloque 7 |
| Control Caballetes › Caballetes | `form:Caballetes` | 5249 | Extensión vidrio | Bloque 7 |
| Costos › Elementos | `form:operarios` | 4411 | Pendiente | Costes de producción |
| Costos › Tipos de Recursos | `form:tipo_recurso_maestro` | 921 | Pendiente | Costes de producción |
| Costos › Escala costos | `prg:recargos_generales_costo` | 3 | Pendiente | Costes de producción |
| Costos › Recursos | `form:tipo_recursos` | 4441 | Pendiente | Costes de producción |
| Costos › Tareas | `form:tipo_tarea` | 4829 | Pendiente | Costes de producción |

## Documentos

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| De Ventas › Creación de Documentos | `prg:lineas_documentos_clientes` | 22 | Parcial | /ventas. Las líneas del legado calculan medidas, recargos y figuras del vidrio (bloque 7). |
| De Ventas › Ver Documentos | `prg:ver_documentos_clientes` | 40 | Hecho | /ventas |
| De Ventas › Facturación Automática | `form:facturacion_automatica` | 1812 | Pendiente | Bloque 3, pendiente de decisiones. |
| De Ventas › Buscar Documentos | `form:busqueda_de_documentos` | — | Parcial | Búsqueda y filtros de /ventas. |
| De Ventas › Ver Documentos Borrados | `prg:ver_documentos_borrados_clientes` | 5 | Revisar | PERA no borra documentos expedidos; ver qué se borra en el legado. |
| De Compra › Creación de Documento | `prg:lineas_documentos_proveedores` | 28 | Hecho | /compras |
| De Compra › Ver Documentos Facturables | `prg:ver_documentos_facturables_proveedores` | 5 | Parcial | Albaranes de entrada pendientes de factura en /compras. |
| De Compra › Ver Documentos | `prg:ver_documentos_proveedores.prg` | 34 | Hecho | /compras |
| De Compra › Ver Documentos Borrados | `prg:ver_documentos_borrados_proveedores` | 5 | Revisar |  |
| Diario Producción | `form:diario_produccion` | 909 | Pendiente | Producción |

## Listados

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| De Artículos › Tarifas de Artículos | `form:listado_tarifas` | 855 | Parcial | Centro de impresión /impresion; menos variantes. |
| De Artículos › Varios de Artículos | `form:listado_articulos` | 7274 | Parcial | Centro de impresión /impresion. |
| De Artículos › Varios de Almacén | `form:listado_articulos_almacen` | 9414 | Parcial | Existencias y diario en /almacen. |
| De Artículos › Etiquetas Artículos | `form:imprime_etiquetas_articulos` | 2755 | Pendiente | Etiquetas (bloque 6). |
| De Documentos › De Clientes › Documentos NO Facturas | `prg:listado_documentos_clientes` | 9 | Parcial | Centro de impresión /impresion. |
| De Documentos › De Clientes › Facturas | `prg:listado_facturas_clientes` | 9 | Parcial | Centro de impresión /impresion. |
| De Documentos › De Proveedores › Documentos NO Facturas | `prg:listado_documentos_proveedores` | 9 | Parcial |  |
| De Documentos › De Proveedores › Facturas | `prg:listado_facturas_proveedores` | 9 | Parcial |  |
| De Clientes | `form:listado_clientes` | 16753 | Parcial | Centro de impresión /impresion. |
| De Proveedores | `prg:listado_proveedores` | 5 | Parcial | Centro de impresión /impresion. |
| De Etiquetas › Caballetes | `form:etiquetas_caballetes` | 371 | Extensión vidrio | Bloque 7 |

## Estadisticas

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Por Año/Mes de Clientes | `prg:estadisticas_historico_clientes` | 5 | Parcial | Panel económico; sin desglose por cliente y mes. |
| Por Año/Mes de Proveedores | `prg:estadisticas_historico_proveedores` | 5 | Pendiente |  |
| Por Año/Mes de Artículos | `form:estadisticas_articulos_historico_mes_a_mes` | 840 | Pendiente |  |
| Comparativa de Clientes | `form:estadisticas_listado_comparativo_mes_a_mes` | 1305 | Parcial | Panel comparativo de periodos. |
| Facturas en curso › En General | `prg:estadisticas_en_general` | 5 | Parcial | Panel de inicio. |
| Facturas en curso › Graficos artículos mes a mes | `form:estadisticas_articulos_facturados_mes_a_mes` | 849 | Pendiente |  |
| Facturas en curso › Graficos clientes mesa mes | `form:estadisticas_clientes_mes_a_mes` | 927 | Parcial | Panel económico. |

## Herramientas

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Rehacer Contadores | `form:codigos` | 1860 | No aplica | La numeración de PERA es transaccional y no se descuadra. |
| Limpiar Reports | `form:limpia_reports` | 220 | No aplica | Mantenimiento de ficheros de VFP. |
| Traspasar datos | `prg:traspaso` | 23 | Parcial | Importación CSV/Excel de clientes, proveedores y productos. Falta el importador de los DBF. |
| Cargar Campos Búsquedas | `form:carga_campos_busquedas` | 693 | No aplica | Índices de búsqueda de VFP. |
| Seguimiento de Documentos › Diario de Documentos | `form:diario_documentos` | 1377 | Hecho | /historial (auditoría) |
| Seguimiento de Documentos › Diario de Documentos Borrados | `form:diario_documentos_borrados` | 1014 | Parcial | /historial |
| Seguimiento de Documentos › Diario de Proceso | `form:diario_procesos` | 549 | Parcial | /historial |
| Seguimiento de Documentos › Ver Documentos Borrados › Clientes | `prg:ver_documentos_borrados_clientes` | 5 | Revisar |  |
| Seguimiento de Documentos › Ver Documentos Borrados › Proveedores | `prg:ver_documentos_borrados_proveedores` | 5 | Revisar |  |
| Traducción › Idiomas | `form:idioma` | 991 | Parcial | PERA está en español e inglés; sin más idiomas configurables. |
| Traducción › Formulario a Traducir | `form:traduce_formulario_antes` | 1064 | No aplica | Las traducciones van en el código. |
| Traducción › Reports a Traducir | `form:traduce_reports_antes` | 686 | Parcial | Documentos en el idioma del cliente: pendiente con las plantillas. |
| Carga de Bancos y Sucursales | `form:carga_sucursales_bancarias` | 596 | Revisar |  |
| Utilidades DIMPRO | `form:utilidades_dimpro` | 23728 | Revisar | Utilidades internas de mantenimiento; ver una a una. |
| Cambio de Precios desde Excel › De artículos | `form:actualiza_precios_articulos` | 15714 | Pendiente | Actualización masiva de precios. |
| Cambio de Precios desde Excel › De Clientes | `form:actualiza_precios_clientes` | 13314 | Pendiente | Actualización masiva de precios por cliente. |
| Cambio de Precios desde Excel › De Proveedores | `prg:actualiza_precios_Proveedores_excel` | 5 | Pendiente |  |
| Variables de Entorno | `form:variables_entorno` | 389 | No aplica | Configuración de la instalación de VFP. |
| Cruce Facturas | `form:cruce_datos_facturas` | 879 | Revisar |  |
| Pasar al historico | `form:paso_historico` | 1235 | No aplica | PERA no separa histórico. |
| Traspaso artículos a Lisec. Li.Prod. | `form:traspaso_lisec` | 3000 | Extensión vidrio | Integración con Lisec. Bloque 7. |
| Archivado de acumulados | `form:acumulado_general` | 1398 | No aplica | Acumulados de VFP. |

## Búsqueda en Árbol

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Clientes › Tipos de Clientes | `form:arbol_tipos_de_clientes` | 895 | Parcial | Filtros de /clientes. |
| Clientes › Formas de Pago | `form:arbol_clientes_forma_pago` | 944 | Parcial | Filtros de /clientes. |
| Artículos › Tipos de Artículos › Por Precios | `form:arbol_tipos_de_articulos` | 884 | Parcial | Filtros de /catalogo. |
| Artículos › Tipos de Artículos › Por Documentos | `form:arbol_articulos_albaranes` | 992 | Pendiente |  |
| Artículos › Familias de Artículos | `form:arbol_familias_subfamilias_articulos` | 892 | Parcial | Filtros de /catalogo. |
| Documentos › Por Vendedor | `form:arbol_vendedor_documentos` | 915 | Parcial | /comisiones filtra por comercial; /ventas aún no. |
| Documentos › Por Tipo de Documento | `form:arbol_series_documentos` | 913 | Hecho | Filtro por tipo en /ventas. |
| Documentos › Por Forma de Pago | `form:arbol_albaranes_forma_pago` | 974 | Pendiente |  |
| Documentos › Por Referencia Obra | `form:arbol_ref_obra` | 959 | Pendiente |  |
| Aluminio › Por Series de Aluminio | `form:arbol_series_aluminio` | 917 | Extensión vidrio | Bloque 7 |

## Aluminio

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Series | `form:series_aluminio` | 2017 | Extensión vidrio | Bloque 7 |
| Vidrios | `form:vidrios_aluminio` | 1193 | Extensión vidrio | Bloque 7 |

## Transporte

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Tipo Transporte | `form:camion` | 1278 | Hecho | Vehículos en /operaciones |
| Transportista | `form:transportista` | 1645 | Hecho | Transportistas en /operaciones |
| Salidas | `form:salidas` | 7608 | Parcial | Rutas y expediciones en /operaciones; revisar lo que hace «Salidas». |
| Eliminar salidas | `form:salidas_borrar` | 457 | Parcial | /operaciones |

## Agenda

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Tipos detalles agenda | `form:tipos_detalle_agenda` | 556 | Hecho | /agenda › Tipos |
| Agenda | `form:agenda` | 3734 | Hecho | /agenda |

## Reclamaciones

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Motivo | `form:motivos` | 538 | Hecho | /reclamaciones › Tablas |
| No Conformidad | `form:no_conformidad` | 553 | Hecho | /reclamaciones › Tablas |
| Causa | `form:causas` | 533 | Hecho | /reclamaciones › Tablas |
| Área | `form:areas` | 541 | Hecho | /reclamaciones › Tablas |
| Redactor-Responsable | `form:cargos` | 761 | Hecho | /reclamaciones › Tablas |
| Resolución | `form:solucion` | 544 | Hecho | /reclamaciones › Tablas |
| Acción Preventiva | `form:accion_preventiva` | 578 | Hecho | /reclamaciones › Tablas |
| Reclamación | `form:reclamaciones` | 14345 | Hecho | /reclamaciones |

## iPedidos

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Genera Ficheros | `form:ficheros_para_ipedidos` | 491 | Revisar | Integración con iPedidos (pedidos de clientes). |
| Incorpora desde iPedidos | `form:albaran_desde_ipedidos` | 2541 | Revisar | Integración con iPedidos. |

## VidrioService

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| VS-DELIVERY › Caballetes | `form:vs_floor_caballetes` | 1687 | Extensión vidrio | Bloque 7 |
| VS-STOCK | `form:vs_stock` | 1033 | Extensión vidrio | Bloque 7 |

## Acerca de...

| Opción | Abre | Tamaño | Estado | En PERA / qué falta |
|---|---|---|---|---|
| Acerca de... | `form:a_cercade` | 1697 | No aplica | Información de la versión (en PERA, en el pie del menú). |

## Pantallas internas

No están en el menú: se abren desde otras pantallas. Son las de más código.

| Pantalla | Tamaño | Estado | Nota |
|---|---|---|---|
| `documentos` | 105484 | Parcial | Formulario central de albaranes, facturas y presupuestos. En PERA, /ventas; falta lo de vidrio y algunas acciones. |
| `lineas_documento_inslinea` | 120576 | Parcial | Alta de líneas con medidas, figuras, recargos y cálculo de vidrio. Lo general está en /ventas; lo de vidrio, bloque 7. |
| `lineas_documento_gestion_residuos` | 69446 | Pendiente | Gestión de residuos en las líneas: revisar qué exige. |
| `articulos` | 26359 | Parcial | Ficha completa del artículo. |
| `listado_documentos` | 30760 | Parcial | Listados de documentos. |
| `listado_facturas` | 23385 | Parcial | Listados de facturas. |
| `ver_documentos` | 12238 | Hecho | Lista de documentos. |
| `clientes_en_general` | 9842 | Hecho | Ficha de cliente (doc 23). |
| `listados_gr_facturas` | 8102 | Parcial | Listados gráficos de facturas. |
| `estadisticas` | 3318 | Parcial | Estadísticas. |
| `importacion_documentos_externos` | 1611 | Pendiente | Importar documentos de otros sistemas. |
| `mapa_situacion` | 245 | Pendiente | Mapas de situación de clientes y rutas. |

## Siguientes pasos

1. **Revisar** las opciones marcadas así: abrir cada pantalla y decidir.
2. **Diccionario de datos**: cada una de las 301 tablas, campo a campo, con su sitio en PERA; es también la base del importador de los datos de los clientes.
3. **Cálculos con pruebas de equivalencia**: precios, descuentos, recargos, vencimientos y cálculos de vidrio, con ejemplos sacados de los datos reales de Dimpro.
4. **Por bloques**, empezando por lo que más usan los clientes: documentos de venta y sus líneas, facturación automática (bloque 3), plantillas (bloque 6) y vidrio (bloque 7).
