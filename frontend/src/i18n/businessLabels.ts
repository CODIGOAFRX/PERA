import type { DocumentStatus, DocumentType, PaymentStatus, RiskPolicy, TaxIdentificationType, UnitOfMeasure } from '../types/api'
import type { TranslationKey } from './catalogs'

export const documentTypeKey: Record<DocumentType, TranslationKey> = {
  QUOTE: 'document.type.QUOTE',
  SALES_ORDER: 'document.type.SALES_ORDER',
  DELIVERY_NOTE: 'document.type.DELIVERY_NOTE',
  INVOICE: 'document.type.INVOICE',
  RECTIFYING_INVOICE: 'document.type.RECTIFYING_INVOICE',
  WORK_ORDER: 'document.type.WORK_ORDER',
}

export const taxIdentificationTypeKey: Record<TaxIdentificationType, TranslationKey> = {
  NIF: 'tax.id.NIF',
  VAT_NUMBER: 'tax.id.VAT_NUMBER',
  PASSPORT: 'tax.id.PASSPORT',
  FOREIGN_OFFICIAL_ID: 'tax.id.FOREIGN_OFFICIAL_ID',
  RESIDENCE_CERTIFICATE: 'tax.id.RESIDENCE_CERTIFICATE',
  OTHER_DOCUMENT: 'tax.id.OTHER_DOCUMENT',
  NOT_REGISTERED: 'tax.id.NOT_REGISTERED',
}

export const documentStatusKey: Record<DocumentStatus, TranslationKey> = {
  DRAFT: 'document.status.DRAFT',
  CONFIRMED: 'document.status.CONFIRMED',
  CONVERTED: 'document.status.CONVERTED',
  CANCELLED: 'document.status.CANCELLED',
}

export const paymentStatusKey: Record<PaymentStatus, TranslationKey> = {
  NOT_APPLICABLE: 'payment.status.NOT_APPLICABLE',
  PENDING: 'payment.status.PENDING',
  PARTIALLY_PAID: 'payment.status.PARTIALLY_PAID',
  PAID: 'payment.status.PAID',
}

export const riskPolicyKey: Record<RiskPolicy, TranslationKey> = {
  WARN: 'risk.policy.WARN',
  REQUIRE_CONFIRMATION: 'risk.policy.REQUIRE_CONFIRMATION',
  BLOCK: 'risk.policy.BLOCK',
}

export const unitKey: Record<UnitOfMeasure, TranslationKey> = {
  UNIT: 'unit.UNIT',
  METER: 'unit.METER',
  SQUARE_METER: 'unit.SQUARE_METER',
  CUBIC_METER: 'unit.CUBIC_METER',
  KILOGRAM: 'unit.KILOGRAM',
  LITER: 'unit.LITER',
  HOUR: 'unit.HOUR',
}
