# Visión general

PERA ERP moderniza las capacidades útiles de Crystal Win Dimpro para empresas industriales y comerciales. Se construye desde cero con dominio, datos y documentación propios.

## Objetivos

- Operar varias empresas desde una misma plataforma, con acceso y datos aislados por empresa.
- Reducir la navegación con un futuro dashboard y accesos directos a las tareas frecuentes.
- Cubrir el flujo comercial inicial: clientes, proveedores, artículos, presupuesto, albarán, factura y cobro.
- Dejar límites claros para incorporar vencimientos, recibos, remesas, riesgo, cartera, caja y factura electrónica.
- Poder desplegar y evolucionar cada contexto sin reconstruir un monolito acoplado.

## Alcance del MVP

1. Login, empresa activa, usuarios y roles básicos.
2. Altas y consultas de clientes, proveedores y artículos.
3. Presupuestos, albaranes y facturas simples con cálculo reproducible.
4. Conversión presupuesto → albarán → factura.
5. Formas de pago, generación de vencimientos y estado pendiente/parcial/cobrado.

## Fuera del primer MVP

- Cartera y diario financiero completos.
- Remesas bancarias operativas y formatos bancarios.
- Factura electrónica y cumplimiento normativo específico.
- Contabilidad, stock, producción e integraciones externas.
- Informes avanzados, WebSockets y automatizaciones de alto volumen.

## Criterios de éxito del primer flujo

Un usuario autorizado entra en una empresa, crea cliente/proveedor/artículo, emite un presupuesto, lo convierte en albarán y factura, genera vencimientos y puede consultar su situación de cobro sin ver datos de otra empresa.
