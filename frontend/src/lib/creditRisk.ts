import { ApiError } from './api'
import { formatCurrency } from './format'

export type CreditRiskPolicy = 'WARN' | 'REQUIRE_CONFIRMATION' | 'BLOCK'

/** Server assessment: pending invoices plus the new document, in the company base currency. */
export interface CreditRiskAssessment {
  level: 'OK' | 'WARNING' | 'OVER_LIMIT'
  policy: CreditRiskPolicy
  creditLimit: number
  warningThreshold: number
  outstanding: number
  documentAmount: number
  projected: number
  canOverride: boolean
}

/** Figures returned with a 409 when a document needs a decision or is blocked. */
export interface CreditRiskProblem {
  detail?: string
  policy: CreditRiskPolicy
  creditLimit: number
  outstanding: number
  documentAmount: number
  projected: number
  requiresAcknowledgement: boolean
  blocked: boolean
}

/** Document types whose creation is checked against the customer's credit. */
export const creditCheckedTypes = new Set(['SALES_ORDER', 'DELIVERY_NOTE', 'INVOICE'])

export function creditRiskOf(cause: unknown): CreditRiskProblem | null {
  if (!(cause instanceof ApiError) || cause.status !== 409) return null
  const problem = cause.problem as (CreditRiskProblem & { type?: string }) | undefined
  return problem?.type?.endsWith('/credit-risk') ? problem : null
}

export function creditRiskFigures(risk: Pick<CreditRiskProblem, 'outstanding' | 'documentAmount' | 'projected' | 'creditLimit'>, locale: string, language: 'es' | 'en', currency = 'EUR') {
  const money = (value: number) => formatCurrency(value, currency, locale)
  return language === 'es'
    ? `Pendiente de cobro: ${money(risk.outstanding)} · Este documento: ${money(risk.documentAmount)} · Total: ${money(risk.projected)} · Límite de crédito: ${money(risk.creditLimit)}.`
    : `Outstanding: ${money(risk.outstanding)} · This document: ${money(risk.documentAmount)} · Total: ${money(risk.projected)} · Credit limit: ${money(risk.creditLimit)}.`
}

/**
 * Runs an action that the server may stop for credit risk. When the user may continue, asks for
 * confirmation and repeats it with the acknowledgement; otherwise rethrows the server error.
 */
export async function withCreditRiskConfirmation<T>(
  action: (acknowledged: boolean) => Promise<T>,
  confirm: (options: { title: string; message: string; confirmLabel: string; danger: boolean }) => Promise<boolean>,
  locale: string,
  language: 'es' | 'en',
): Promise<T | null> {
  try {
    return await action(false)
  } catch (cause) {
    const risk = creditRiskOf(cause)
    if (!risk || !risk.requiresAcknowledgement) throw cause
    const accepted = await confirm({
      title: language === 'es' ? 'Límite de crédito superado' : 'Credit limit exceeded',
      message: `${risk.detail ?? ''} ${creditRiskFigures(risk, locale, language)}`.trim(),
      confirmLabel: language === 'es' ? 'Continuar igualmente' : 'Continue anyway',
      danger: true,
    })
    return accepted ? action(true) : null
  }
}
