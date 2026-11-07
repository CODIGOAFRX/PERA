import type { BadgeTone } from '../components/StatusBadge'
import type { VerifactuRecord, VerifactuState } from '../types/api'

/** Orden en que se enseñan los estados: primero lo que pide atención. */
export const verifactuStates: VerifactuState[] = ['REJECTED', 'ACCEPTED_WITH_ERRORS', 'SENT', 'PENDING', 'ACCEPTED']

export function verifactuStateTone(state: VerifactuState): BadgeTone {
  if (state === 'ACCEPTED') return 'success'
  if (state === 'REJECTED') return 'danger'
  if (state === 'ACCEPTED_WITH_ERRORS') return 'warning'
  if (state === 'SENT') return 'info'
  return 'neutral'
}

const labels: Record<VerifactuState, [string, string]> = {
  PENDING: ['Pendiente de remitir', 'Pending submission'],
  SENT: ['Remitido, sin confirmar', 'Submitted, unconfirmed'],
  ACCEPTED: ['Aceptado', 'Accepted'],
  ACCEPTED_WITH_ERRORS: ['Aceptado con errores', 'Accepted with errors'],
  REJECTED: ['Rechazado', 'Rejected'],
}

export const verifactuStateLabel = (state: VerifactuState, language: string) => labels[state][language === 'es' ? 0 : 1]

/** Texto de la AEAT con su código delante, como lo dan sus listas de errores. */
export const verifactuMessage = (record: Pick<VerifactuRecord, 'aeatErrorCode' | 'aeatMessage'>) =>
  record.aeatMessage ? `${record.aeatErrorCode ? `${record.aeatErrorCode} · ` : ''}${record.aeatMessage}` : ''

/**
 * Explicación para el usuario de un rechazo o defecto de la AEAT: qué ha pasado y qué hacer. El texto de la
 * AEAT es técnico («el bloque Destinatarios tiene que estar cumplimentado»); aquí se traduce a lo que el
 * usuario ve en PERA. Se reconoce por el código y, si no, por el texto, que es lo que de verdad se repite.
 */
export function aeatErrorExplanation(record: Pick<VerifactuRecord, 'aeatErrorCode' | 'aeatMessage'>, language: string): { cause: string; action: string } | null {
  const es = language === 'es'
  const text = (record.aeatMessage ?? '').toLowerCase()
  const code = record.aeatErrorCode ?? ''
  if (!text && !code) return null
  const pick = (causeEs: string, actionEs: string, causeEn: string, actionEn: string) => es ? { cause: causeEs, action: actionEs } : { cause: causeEn, action: actionEn }
  if (code === '1189' || text.includes('destinatarios tiene que estar cumplimentado')) {
    return pick('La factura es completa y el cliente no tenía NIF. Hacienda no admite una factura completa sin el NIF del cliente.',
      'Pon el NIF en la ficha del cliente antes de facturarle. PERA ya no deja expedir una factura completa a un cliente sin NIF.',
      'The invoice is a full invoice and the customer had no tax ID. AEAT does not accept a full invoice without the customer tax ID.',
      'Add the tax ID to the customer record before invoicing. PERA no longer lets you issue a full invoice to a customer without one.')
  }
  if (text.includes('sistemainformatico') || text.includes('sistema informatico')) {
    return pick('Los datos del fabricante del programa (NIF y nombre del productor de PERA) no coinciden con el censo de Hacienda. No es un fallo de la factura ni del cliente.',
      'Avisa a soporte: hay que corregir la configuración de la instalación.',
      'The software producer data (PERA producer tax ID and name) does not match the AEAT census. It is not an invoice or customer error.',
      'Contact support: the installation settings must be fixed.')
  }
  if (text.includes('censo') || text.includes('no identificado') || text.includes('no está identificado')) {
    return pick('El NIF o el nombre del cliente no coinciden con los que tiene Hacienda.',
      'Revisa en la ficha del cliente el NIF y la razón social tal como figuran en Hacienda.',
      'The customer tax ID or name does not match the AEAT records.',
      'Check the customer tax ID and legal name in the customer record.')
  }
  if (code === '1123' || text.includes('encadenamiento') || text.includes('registroanterior')) {
    return pick('El registro no encaja con el anterior que tiene Hacienda (cadena de registros).',
      'Avisa a soporte; no vuelvas a expedir la factura.',
      'The record does not chain with the previous one AEAT holds.',
      'Contact support; do not issue the invoice again.')
  }
  if (text.includes('certificado') || text.includes('apoderamiento') || text.includes('representa')) {
    return pick('El certificado con el que se ha enviado no está autorizado para presentar las facturas de esta empresa.',
      'Revisa el certificado en Conexiones (o en el factura_e.ini de la empresa) o el apoderamiento en Hacienda.',
      'The certificate used is not authorised to submit this company’s invoices.',
      'Check the certificate in Connections (or the company factura_e.ini) or the authorisation at AEAT.')
  }
  return pick('Hacienda la ha rechazado por el motivo que aparece arriba.',
    'Si no está claro qué corregir, avisa a soporte con el número de la factura.',
    'AEAT rejected it for the reason shown above.',
    'If it is unclear what to fix, contact support with the invoice number.')
}
