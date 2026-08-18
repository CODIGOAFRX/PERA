# Frontend MVP

La primera interfaz de PERA ERP cubre el flujo comercial que ya tiene soporte real en el backend:

- autenticación y selección de empresa;
- resumen económico con KPIs, evolución acumulada actual/anterior, perspectiva de seis meses y ritmo esperado;
- consulta, alta y edición de clientes, proveedores y productos;
- creación y consulta de presupuestos, albaranes, facturas y partes de trabajo;
- conversión presupuesto → albarán → factura;
- formas de pago, consulta y generación de vencimientos;
- actualización del estado de cobro de facturas.
- administración de usuarios y cinco perfiles de acceso aplicados al menú, rutas y APIs.
- centro de informes por módulo con selección de columnas, filtros, ordenación, vista previa y salida A4 para impresión o guardado como PDF.

## Informes e impresión

La ruta `/impresion` presenta tarjetas de clientes, proveedores, productos, presupuestos, ventas y cobros pendientes. Las tarjetas se filtran por perfil: economía accede a clientes y documentos económicos, logística a proveedores y catálogo a productos; propietario y administrador pueden utilizar todas.

Cada informe permite seleccionar campos recomendados o todos los disponibles, buscar registros, filtrar por estado, tipo o fechas según el módulo, elegir el criterio de orden y personalizar el título. La vista previa mantiene formatos de idioma y moneda, separa los totales por divisa y genera una hoja A4 apaisada sin navegación ni controles al imprimir. La opción del navegador permite enviarla a impresora o guardarla como PDF.

El informe de cobros pendientes calcula el saldo con los vencimientos existentes (`importe - cobrado`). Si una factura pendiente todavía no tiene calendario de vencimientos, usa el total de la factura para no ocultar deuda. La carga recorre todas las páginas de las APIs propietarias y conserva un límite operativo de 50.000 filas por documento.

## Criterios visuales

La interfaz usa blanco, crema muy suave y un verde pera vivo. La marca emplea una pera lineal en SVG en lugar de una inicial. No depende de animaciones para comunicar estado, mantiene foco visible, respeta `prefers-reduced-motion` y adapta navegación, formularios y paneles a escritorio y móvil. La barra lateral se puede redimensionar con puntero o teclado; se compacta y termina ocultándose, con un control para recuperarla.

## Arquitectura

React conserva la sesión en el navegador, extrae del JWT firmado la identidad, roles y permisos para adaptar la experiencia, adjunta ese JWT a cada llamada y centraliza la traducción de errores `ProblemDetail`. El backend vuelve a comprobar cada permiso y es la autoridad final. Un enrutador pequeño basado en History API evita una dependencia adicional. En desarrollo, Vite reenvía `/api` al gateway; en Docker lo hace Nginx.

## Validación realizada

- Suite frontend automatizada, incluida la preparación y separación por perfiles de los informes.
- Compilación TypeScript/Vite de producción.
- Auditoría de dependencias de producción sin vulnerabilidades conocidas.
- Recorrido real en navegador sobre PostgreSQL: login, maestros, creación y conversión de documento, factura, vencimientos y cobro.
- Revisión visual en 1280 × 800 y 390 × 844 píxeles.

El frontend es un MVP funcional: compras, inventario avanzado, contabilidad completa, remesas y caja siguen en el roadmap aunque parte de su modelo backend ya exista.
