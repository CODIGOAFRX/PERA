# Frontend MVP

La primera interfaz de PERA ERP cubre el flujo comercial que ya tiene soporte real en el backend:

- autenticación y selección de empresa;
- resumen operativo;
- consulta, alta y edición de clientes, proveedores y productos;
- creación y consulta de presupuestos, albaranes, facturas y partes de trabajo;
- conversión presupuesto → albarán → factura;
- formas de pago, consulta y generación de vencimientos;
- actualización del estado de cobro de facturas.

## Criterios visuales

La interfaz usa blanco, crema muy suave y verde pera apagado. No depende de animaciones para comunicar estado, mantiene foco visible, respeta `prefers-reduced-motion` y adapta navegación, formularios y paneles a escritorio y móvil.

## Arquitectura

React conserva la sesión en el navegador, adjunta el JWT a cada llamada y centraliza la traducción de errores `ProblemDetail`. Un enrutador pequeño basado en History API evita una dependencia adicional. En desarrollo, Vite reenvía `/api` al gateway; en Docker lo hace Nginx.

## Validación realizada

- 10 pruebas frontend automatizadas.
- Compilación TypeScript/Vite de producción.
- Auditoría de dependencias de producción sin vulnerabilidades conocidas.
- Recorrido real en navegador sobre PostgreSQL: login, maestros, creación y conversión de documento, factura, vencimientos y cobro.
- Revisión visual en 1280 × 800 y 390 × 844 píxeles.

El frontend es un MVP funcional: compras, inventario avanzado, contabilidad completa, remesas y caja siguen en el roadmap aunque parte de su modelo backend ya exista.
