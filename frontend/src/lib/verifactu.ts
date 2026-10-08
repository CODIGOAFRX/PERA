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
