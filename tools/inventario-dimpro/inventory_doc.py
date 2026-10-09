"""Genera docs/26: cada opción del menú de DimproCristalWin, su tamaño y su estado en PERA."""
import json
from collections import Counter, OrderedDict

H, P, N, V, X, R = 'Hecho', 'Parcial', 'Pendiente', 'Extensión vidrio', 'No aplica', 'Revisar'

# Clave: ruta del menú. Valor: (estado, dónde está en PERA o qué falta).
STATUS = {
 'Archivo > Empresa': (P, 'Configuración › Empresa. El formulario del legado guarda muchos parámetros de funcionamiento que hay que repasar uno a uno.'),
 'Archivo > Usuarios > Grupo Usuarios': (P, 'Perfiles fijos (propietario, administración, economía, logística, catálogo). Sin grupos con permisos por opción a medida.'),
 'Archivo > Usuarios > Usuarios': (H, '/usuarios'),
 'Archivo > Tarifas > Grupo de Tarifas': (P, 'Tarifas con tarifa madre en /maestros; sin grupos de tarifas como tal.'),
 'Archivo > Tarifas > Tarifas': (H, '/maestros › Tarifas'),
 'Archivo > Articulos > Clase de Artículo': (H, 'Clasificación de productos (naturaleza, supertipo, tipo, grupo) en /maestros'),
 'Archivo > Articulos > Familia/Sub-familias de Artículos': (H, 'Familias y categorías de producto'),
 'Archivo > Articulos > Cámaras Doble Acristalamiento': (V, 'Bloque 7'),
 'Archivo > Articulos > Propiedades Generales': (R, 'Hay que ver qué guarda antes de decidir.'),
 'Archivo > Articulos > Recargos Generales': (V, 'Recargos por medidas del vidrio. Bloque 7.'),
 'Archivo > Articulos > Catalogos Figuras': (V, 'Bloque 7'),
 'Archivo > Articulos > Agrupación': (R, ''),
 'Archivo > Articulos > Tipo de Demora': (R, ''),
 'Archivo > Articulos > Tipo de Artículos': (P, 'Tipos de producto en /maestros. El legado calcula precios por tipo; falta revisarlo.'),
 'Archivo > Articulos > Tipo de Fabricación': (V, 'Bloque 7'),
 'Archivo > Articulos > Tipos de Láminas-Pinturas': (V, 'Bloque 7'),
 'Archivo > Articulos > Almacén': (H, '/almacen › Almacenes'),
 'Archivo > Articulos > Artículo': (P, '/catalogo. La ficha del legado es mucho más amplia (propiedades de vidrio, costes, compras).'),
 'Archivo > Articulos > Diario Almacén': (H, '/almacen › Diario'),
 'Archivo > Articulos > Secciones Fabricación': (N, 'Producción'),
 'Archivo > Agenda Telefónica': (H, '/agenda › Listín'),
 'Archivo > Clientes > Códigos Postales': (N, 'Autocompletar población y provincia.'),
 'Archivo > Clientes > Formas de Pago': (H, 'Formas de pago en Finanzas'),
 'Archivo > Clientes > Vendedores': (H, '/clientes › Comerciales y /comisiones'),
 'Archivo > Clientes > Cobradores': (N, ''),
 'Archivo > Clientes > Grupos de Clientes': (H, '/clientes › Clasificación'),
 'Archivo > Clientes > Asunto Cartas/Documentos': (N, 'Junto a plantillas de documento (bloque 6).'),
 'Archivo > Clientes > Tipos Clientes C.C.': (N, 'Crédito y caución.'),
 'Archivo > Clientes > Rutas > Transportes': (P, 'Rutas de reparto en /operaciones; sin asignar ruta por cliente y día.'),
 'Archivo > Clientes > Rutas > Comerciales': (N, 'Rutas de visita de los comerciales.'),
 'Archivo > Clientes > Forma Entrega': (H, '/clientes › Clasificación'),
 'Archivo > Clientes > Datos de Clientes': (H, '/clientes'),
 'Archivo > Clientes > Actualiza Riesgo Clientes': (P, 'El riesgo se calcula al vender; sin recálculo masivo.'),
 'Archivo > Clientes > Motivos Inactivo': (H, '/clientes › Clasificación'),
 'Archivo > Clientes > Obras clientes': (P, 'Direcciones de entrega del cliente; la obra como tal está congelada.'),
 'Archivo > Proveedores': (H, '/proveedores'),
 'Archivo > Tarifas Especiales > Tarifas Especiales': (H, 'Precios y reglas por cliente en /maestros'),
 'Archivo > Tarifas Especiales > Desglose Tarifas Especiales': (H, 'Precios y reglas por cliente en /maestros'),
 'Archivo > Bancos > Bancos': (R, 'Entidades bancarias; en PERA el banco va como IBAN en cada ficha.'),
 'Archivo > Bancos > Agencias': (R, 'Sucursales; el IBAN las hace innecesarias. Confirmar.'),
 'Archivo > Tipo de Documentos > Estado de los Documentos': (P, 'Estados fijos (borrador, confirmado, convertido, anulado).'),
 'Archivo > Tipo de Documentos > Tipo Factura. (Calendario facturas)': (N, 'Facturación automática (bloque 3).'),
 'Archivo > Diario de Caja > Cuentas Contables': (P, '/contabilidad; revisar si el plan de cuentas cubre lo del legado.'),
 'Archivo > Diario de Caja > Claves Diario de caja': (P, 'Movimientos de caja en /cartera con concepto libre.'),
 'Archivo > Diario de Caja > Origen Apuntes Caja': (P, 'Movimientos de caja en /cartera.'),
 'Archivo > Diario de Caja > Diario de caja': (H, '/cartera › Caja'),
 'Archivo > Grupo Descuento': (R, ''),
 'Archivo > Monedas': (H, 'Configuración › Divisas'),
 'Archivo > Calendario Facturación': (N, 'Facturación automática (bloque 3).'),
 'Archivo > Series de Documentos > Agrupación para listados de Series de Documentos': (N, ''),
 'Archivo > Series de Documentos > Grupo Serie Documentos': (N, ''),
 'Archivo > Series de Documentos > Estados Documentos': (P, 'Estados fijos.'),
 'Archivo > Series de Documentos > Series de Documentos': (H, 'Configuración › Numeraciones'),
 'Archivo > Series de Documentos > Clasificación Documentos': (N, 'Clasificación ISO de documentos.'),
 'Archivo > Medidor/Montador': (V, 'Mediciones y montajes. Bloque 7.'),
 'Archivo > Formas - Figuras > Empresa Figuras': (V, 'Bloque 7'),
 'Archivo > Formas - Figuras > Grupo Formas': (V, 'Bloque 7'),
 'Archivo > Formas - Figuras > Catálogo Figuras': (V, 'Bloque 7'),
 'Archivo > Formas - Figuras > Formas': (V, 'Bloque 7'),
 'Archivo > Tipo Trabajos': (R, ''),
 'Archivo > Días Entrega por Metros': (V, 'Bloque 7'),
 'Archivo > Operarios/Elementos': (N, 'Producción'),
 'Archivo > Control Caballetes > Tipo Caballete': (V, 'Bloque 7'),
 'Archivo > Control Caballetes > Caballetes': (V, 'Bloque 7'),
 'Archivo > Costos > Elementos': (N, 'Costes de producción'),
 'Archivo > Costos > Tipos de Recursos': (N, 'Costes de producción'),
 'Archivo > Costos > Escala costos': (N, 'Costes de producción'),
 'Archivo > Costos > Recursos': (N, 'Costes de producción'),
 'Archivo > Costos > Tareas': (N, 'Costes de producción'),
 'Documentos > De Ventas > Creación de Documentos': (P, '/ventas. Las líneas del legado calculan medidas, recargos y figuras del vidrio (bloque 7).'),
 'Documentos > De Ventas > Ver Documentos': (H, '/ventas'),
 'Documentos > De Ventas > Facturación Automática': (N, 'Bloque 3, pendiente de decisiones.'),
 'Documentos > De Ventas > Buscar Documentos': (P, 'Búsqueda y filtros de /ventas.'),
 'Documentos > De Ventas > Ver Documentos Borrados': (R, 'PERA no borra documentos expedidos; ver qué se borra en el legado.'),
 'Documentos > De Compra > Creación de Documento': (H, '/compras'),
 'Documentos > De Compra > Ver Documentos Facturables': (P, 'Albaranes de entrada pendientes de factura en /compras.'),
 'Documentos > De Compra > Ver Documentos': (H, '/compras'),
 'Documentos > De Compra > Ver Documentos Borrados': (R, ''),
 'Documentos > Diario Producción': (N, 'Producción'),
 'Listados > De Artículos > Tarifas de Artículos': (P, 'Centro de impresión /impresion; menos variantes.'),
 'Listados > De Artículos > Varios de Artículos': (P, 'Centro de impresión /impresion.'),
 'Listados > De Artículos > Varios de Almacén': (P, 'Existencias y diario en /almacen.'),
 'Listados > De Artículos > Etiquetas Artículos': (N, 'Etiquetas (bloque 6).'),
 'Listados > De Documentos > De Clientes > Documentos NO Facturas': (P, 'Centro de impresión /impresion.'),
 'Listados > De Documentos > De Clientes > Facturas': (P, 'Centro de impresión /impresion.'),
 'Listados > De Documentos > De Proveedores > Documentos NO Facturas': (P, ''),
 'Listados > De Documentos > De Proveedores > Facturas': (P, ''),
 'Listados > De Clientes': (P, 'Centro de impresión /impresion.'),
 'Listados > De Proveedores': (P, 'Centro de impresión /impresion.'),
 'Listados > De Etiquetas > Caballetes': (V, 'Bloque 7'),
 'Estadisticas > Por Año/Mes de Clientes': (P, 'Panel económico; sin desglose por cliente y mes.'),
 'Estadisticas > Por Año/Mes de Proveedores': (N, ''),
 'Estadisticas > Por Año/Mes de Artículos': (N, ''),
 'Estadisticas > Comparativa de Clientes': (P, 'Panel comparativo de periodos.'),
 'Estadisticas > Facturas en curso > En General': (P, 'Panel de inicio.'),
 'Estadisticas > Facturas en curso > Graficos artículos mes a mes': (N, ''),
 'Estadisticas > Facturas en curso > Graficos clientes mesa mes': (P, 'Panel económico.'),
 'Herramientas > Rehacer Contadores': (X, 'La numeración de PERA es transaccional y no se descuadra.'),
 'Herramientas > Limpiar Reports': (X, 'Mantenimiento de ficheros de VFP.'),
 'Herramientas > Traspasar datos': (P, 'Importación CSV/Excel de clientes, proveedores y productos. Falta el importador de los DBF.'),
 'Herramientas > Cargar Campos Búsquedas': (X, 'Índices de búsqueda de VFP.'),
 'Herramientas > Seguimiento de Documentos > Diario de Documentos': (H, '/historial (auditoría)'),
 'Herramientas > Seguimiento de Documentos > Diario de Documentos Borrados': (P, '/historial'),
 'Herramientas > Seguimiento de Documentos > Diario de Proceso': (P, '/historial'),
 'Herramientas > Seguimiento de Documentos > Ver Documentos Borrados > Clientes': (R, ''),
 'Herramientas > Seguimiento de Documentos > Ver Documentos Borrados > Proveedores': (R, ''),
 'Herramientas > Traducción > Idiomas': (P, 'PERA está en español e inglés; sin más idiomas configurables.'),
 'Herramientas > Traducción > Formulario a Traducir': (X, 'Las traducciones van en el código.'),
 'Herramientas > Traducción > Reports a Traducir': (P, 'Documentos en el idioma del cliente: pendiente con las plantillas.'),
 'Herramientas > Carga de Bancos y Sucursales': (R, ''),
 'Herramientas > Utilidades DIMPRO': (R, 'Utilidades internas de mantenimiento; ver una a una.'),
 'Herramientas > Cambio de Precios desde Excel > De artículos': (N, 'Actualización masiva de precios.'),
 'Herramientas > Cambio de Precios desde Excel > De Clientes': (N, 'Actualización masiva de precios por cliente.'),
 'Herramientas > Cambio de Precios desde Excel > De Proveedores': (N, ''),
 'Herramientas > Variables de Entorno': (X, 'Configuración de la instalación de VFP.'),
 'Herramientas > Cruce Facturas': (R, ''),
 'Herramientas > Pasar al historico': (X, 'PERA no separa histórico.'),
 'Herramientas > Traspaso artículos a Lisec. Li.Prod.': (V, 'Integración con Lisec. Bloque 7.'),
 'Herramientas > Archivado de acumulados': (X, 'Acumulados de VFP.'),
 'Búsqueda en Árbol > Clientes > Tipos de Clientes': (P, 'Filtros de /clientes.'),
 'Búsqueda en Árbol > Clientes > Formas de Pago': (P, 'Filtros de /clientes.'),
 'Búsqueda en Árbol > Artículos > Tipos de Artículos > Por Precios': (P, 'Filtros de /catalogo.'),
 'Búsqueda en Árbol > Artículos > Tipos de Artículos > Por Documentos': (N, ''),
 'Búsqueda en Árbol > Artículos > Familias de Artículos': (P, 'Filtros de /catalogo.'),
 'Búsqueda en Árbol > Documentos > Por Vendedor': (P, '/comisiones filtra por comercial; /ventas aún no.'),
 'Búsqueda en Árbol > Documentos > Por Tipo de Documento': (H, 'Filtro por tipo en /ventas.'),
 'Búsqueda en Árbol > Documentos > Por Forma de Pago': (N, ''),
 'Búsqueda en Árbol > Documentos > Por Referencia Obra': (N, ''),
 'Búsqueda en Árbol > Aluminio > Por Series de Aluminio': (V, 'Bloque 7'),
 'Aluminio > Series': (V, 'Bloque 7'),
 'Aluminio > Vidrios': (V, 'Bloque 7'),
 'Transporte > Tipo Transporte': (H, 'Vehículos en /operaciones'),
 'Transporte > Transportista': (H, 'Transportistas en /operaciones'),
 'Transporte > Salidas': (P, 'Rutas y expediciones en /operaciones; revisar lo que hace «Salidas».'),
 'Transporte > Eliminar salidas': (P, '/operaciones'),
 'Agenda > Tipos detalles agenda': (H, '/agenda › Tipos'),
 'Agenda > Agenda': (H, '/agenda'),
 'Reclamaciones > Motivo': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > No Conformidad': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Causa': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Área': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Redactor-Responsable': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Resolución': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Acción Preventiva': (H, '/reclamaciones › Tablas'),
 'Reclamaciones > Reclamación': (H, '/reclamaciones'),
 'iPedidos > Genera Ficheros': (R, 'Integración con iPedidos (pedidos de clientes).'),
 'iPedidos > Incorpora desde iPedidos': (R, 'Integración con iPedidos.'),
 'VidrioService > VS-DELIVERY > Caballetes': (V, 'Bloque 7'),
 'VidrioService > VS-STOCK': (V, 'Bloque 7'),
 'Acerca de...': (X, 'Información de la versión (en PERA, en el pie del menú).'),
}

INNER = OrderedDict([
 ('documentos', (P, 'Formulario central de albaranes, facturas y presupuestos. En PERA, /ventas; falta lo de vidrio y algunas acciones.')),
 ('lineas_documento_inslinea', (P, 'Alta de líneas con medidas, figuras, recargos y cálculo de vidrio. Lo general está en /ventas; lo de vidrio, bloque 7.')),
 ('lineas_documento_gestion_residuos', (N, 'Gestión de residuos en las líneas: revisar qué exige.')),
 ('articulos', (P, 'Ficha completa del artículo.')),
 ('listado_documentos', (P, 'Listados de documentos.')),
 ('listado_facturas', (P, 'Listados de facturas.')),
 ('ver_documentos', (H, 'Lista de documentos.')),
 ('clientes_en_general', (H, 'Ficha de cliente (doc 23).')),
 ('listados_gr_facturas', (P, 'Listados gráficos de facturas.')),
 ('estadisticas', (P, 'Estadísticas.')),
 ('importacion_documentos_externos', (N, 'Importar documentos de otros sistemas.')),
 ('mapa_situacion', (N, 'Mapas de situación de clientes y rutas.')),
])

menu = json.load(open('menu_inventory.json', encoding='utf-8'))
inner = json.load(open('inner_screens.json', encoding='utf-8'))
options = [i for i in menu if i['kind'] == 'option']
missing = [' > '.join(i['path']) for i in options if ' > '.join(i['path']).replace('\\', '') not in STATUS]
if missing:
    raise SystemExit('Sin clasificar: ' + '; '.join(missing))

def status_of(item):
    return STATUS[' > '.join(item['path']).replace('\\', '')]

counts = Counter(status_of(i)[0] for i in options)
total = len(options)
lines = []
w = lines.append
w('# 26 — Inventario de DimproCristalWin frente a PERA\n')
w('Fecha de corte: 9 de octubre de 2026\n')
w('Inventario opción por opción del menú principal de DimproCristalWin (`MENUS\\PRINCIPAL.MPR`), con lo que abre cada una, el tamaño de su código y su estado en PERA. Es la base para traer a PERA todo lo que hace el programa anterior: se copia **lo que hace** (opciones, campos, reglas y cálculos, comprobados con datos reales), no la estructura de su código, que es de formularios de Visual FoxPro sobre ficheros DBF y arrastra fallos conocidos.\n')
w('Se ha generado leyendo el código fuente: el menú, los formularios (`FORMS\\*.SCT`) y los programas (`PROGS\\*.prg`). Afina y sustituye al [mapa del doc 19](19-mapa-migracion-dimprocristalwin.md), que iba por áreas.\n')
w('## Resumen\n')
w(f'{total} opciones de menú en 13 menús.\n')
w('| Estado | Opciones | Qué significa |')
w('|---|---|---|')
meaning = {H: 'PERA ya lo hace.', P: 'PERA lo hace en parte; falta lo que dice la nota.', N: 'Falta en PERA y es del núcleo común.',
           V: 'Propio del vidrio: va en la extensión de vidrio (bloque 7).', R: 'Hay que abrir la pantalla y ver qué hace antes de decidir.',
           X: 'Mantenimiento propio de Visual FoxPro o de cómo guarda los datos; PERA no lo necesita.'}
for st in (H, P, N, V, R, X):
    w(f'| {st} | {counts.get(st, 0)} ({round(100 * counts.get(st, 0) / total)} %) | {meaning[st]} |')
w('')
w('El tamaño es orientativo: son las líneas del formulario o programa, incluidas las propiedades de los controles. Sirve para comparar, no como medida exacta del código.\n')
w('Lo más grande no está en el menú sino en las pantallas a las que llaman, sobre todo el formulario de documentos y el de líneas de documento. Ahí está el corazón del programa (ver «Pantallas internas»).\n')

current = None
for item in menu:
    if item['depth'] == 0:
        current = item['path'][0]
        w(f'## {current}\n')
        w('| Opción | Abre | Tamaño | Estado | En PERA / qué falta |')
        w('|---|---|---|---|---|')
        if item['kind'] == 'option':
            st, note = status_of(item)
            s = item.get('scan')
            w(f"| {current} | `{item.get('target', '')}` | {s['code_lines'] if s else '—'} | {st} | {note} |")
        continue
    if item['kind'] == 'submenu':
        continue
    st, note = status_of(item)
    s = item.get('scan')
    label = ' › '.join(item['path'][1:]).replace('\\', '')
    w(f"| {label} | `{item.get('target', '')}` | {s['code_lines'] if s else '—'} | {st} | {note} |")
    if item is menu[-1] or menu[menu.index(item) + 1]['depth'] == 0:
        w('')
w('')
w('## Pantallas internas\n')
w('No están en el menú: se abren desde otras pantallas. Son las de más código.\n')
w('| Pantalla | Tamaño | Estado | Nota |')
w('|---|---|---|---|')
for name, (st, note) in INNER.items():
    s = (inner.get(name) or {}).get('scan')
    w(f"| `{name}` | {s['code_lines'] if s else '—'} | {st} | {note} |")
w('')
w('## Siguientes pasos\n')
w('1. **Revisar** las opciones marcadas así: abrir cada pantalla y decidir.')
w('2. **Diccionario de datos**: cada una de las 301 tablas, campo a campo, con su sitio en PERA; es también la base del importador de los datos de los clientes.')
w('3. **Cálculos con pruebas de equivalencia**: precios, descuentos, recargos, vencimientos y cálculos de vidrio, con ejemplos sacados de los datos reales de Dimpro.')
w('4. **Por bloques**, empezando por lo que más usan los clientes: documentos de venta y sus líneas, facturación automática (bloque 3), plantillas (bloque 6) y vidrio (bloque 7).')
from pathlib import Path
open(Path(__file__).resolve().parents[2] / 'docs' / '26-inventario-dimprocristalwin.md', 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print(dict(counts), total)
