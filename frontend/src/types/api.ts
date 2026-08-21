export interface PageMetadata {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface PageResponse<T> {
  content: T[]
  page: PageMetadata
}

export interface CompanyOption {
  id: string
  code: string
  name: string
}

export interface LoginResponse {
  accessToken: string | null
  tokenType: string | null
  expiresInSeconds: number
  companySelectionRequired: boolean
  companies: CompanyOption[]
}

export interface ManagedUser {
  id: string
  username: string
  displayName: string
  email: string | null
  companyId: string
  roles: string[]
  active: boolean
}

export interface RoleProfile {
  code: string
  name: string
  permissions: string[]
}

export interface MonthlyRevenuePoint {
  month: string
  total: number
}

export interface DailyRevenuePoint {
  day: number
  currentCumulative: number | null
  previousCumulative: number
}

export interface SalesDashboardAnalytics {
  currency: string
  asOfDate: string
  currentMonthTotal: number
  previousMonthTotal: number
  previousMonthToDate: number
  expectedByToday: number
  varianceAmount: number
  performancePercentage: number
  monthProgressPercentage: number
  monthlyRevenue: MonthlyRevenuePoint[]
  dailyRevenue: DailyRevenuePoint[]
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  violations?: Record<string, string>
}

export type RiskPolicy = 'WARN' | 'REQUIRE_CONFIRMATION' | 'BLOCK'

/** Tipo de identificación fiscal. NIF para residentes; el resto viaja como IDOtro en Veri*Factu. */
export type TaxIdentificationType =
  | 'NIF' | 'VAT_NUMBER' | 'PASSPORT' | 'FOREIGN_OFFICIAL_ID'
  | 'RESIDENCE_CERTIFICATE' | 'OTHER_DOCUMENT' | 'NOT_REGISTERED'

export interface Customer {
  id: string
  partyId: string
  code: string
  legalName: string
  tradeName: string | null
  taxId: string | null
  taxIdentificationType: TaxIdentificationType | null
  taxCountryCode: string | null
  phone: string | null
  email: string | null
  observations: string | null
  active: boolean
  priceListId: string | null
  defaultPaymentMethodId: string | null
  supplierCode: string | null
  calculationMultiplier: number
  creditLimit: number
  riskWarningThreshold: number
  riskPolicy: RiskPolicy
  createdAt: string
}

export interface CustomerInput {
  code: string
  legalName: string
  tradeName?: string | null
  taxId?: string | null
  taxIdentificationType?: TaxIdentificationType | null
  taxCountryCode?: string | null
  phone?: string | null
  email?: string | null
  observations?: string | null
  priceListId?: string | null
  defaultPaymentMethodId?: string | null
  supplierCode?: string | null
  creditLimit?: number
  riskWarningThreshold?: number
  riskPolicy?: RiskPolicy
  active?: boolean
}

export interface Supplier {
  id: string
  partyId: string
  code: string
  legalName: string
  tradeName: string | null
  taxId: string | null
  phone: string | null
  email: string | null
  active: boolean
  carrier: string | null
  route: string | null
  defaultPaymentMethodId: string | null
  observations: string | null
  createdAt: string
}

export interface SupplierInput {
  code: string
  legalName: string
  tradeName?: string | null
  taxId?: string | null
  phone?: string | null
  email?: string | null
  observations?: string | null
  carrier?: string | null
  route?: string | null
  defaultPaymentMethodId?: string | null
  active?: boolean
}

export type UnitOfMeasure = 'UNIT' | 'METER' | 'SQUARE_METER' | 'CUBIC_METER' | 'KILOGRAM' | 'LITER' | 'HOUR'

export interface Product {
  id: string
  code: string
  name: string
  description: string | null
  productTypeId: string | null
  productGroupId?: string | null
  taxCodeId?: string | null
  familyId: string | null
  categoryId: string | null
  unitOfMeasure: UnitOfMeasure
  basePrice: number
  taxRate: number
  active: boolean
  createdAt: string
}

export interface ProductInput {
  code: string
  name: string
  description?: string | null
  productTypeId?: string | null
  productGroupId?: string | null
  taxCodeId?: string | null
  familyId?: string | null
  categoryId?: string | null
  unitOfMeasure: UnitOfMeasure
  basePrice: number
  taxRate: number
  active: boolean
}

export type DocumentType = 'QUOTE' | 'SALES_ORDER' | 'DELIVERY_NOTE' | 'INVOICE' | 'RECTIFYING_INVOICE' | 'WORK_ORDER'
export type VerifactuState = 'PENDING' | 'SENT' | 'ACCEPTED' | 'ACCEPTED_WITH_ERRORS' | 'REJECTED'
export type VerifactuRecordType = 'ALTA' | 'ANULACION'

export interface VerifactuRecord {
  id: string
  documentId: string
  recordType: VerifactuRecordType
  sequenceNumber: number
  issuerTaxId: string
  invoiceNumber: string
  invoiceDate: string
  invoiceKind: InvoiceKind | null
  totalTaxAmount: number
  totalAmount: number
  previousFingerprint: string | null
  fingerprint: string
  generatedAt: string
  state: VerifactuState
  aeatCsv: string | null
  /** Contenido exacto del QR de cotejo, construido por el servidor. */
  qrPayload: string | null
}

/** TipoFactura de Veri*Factu. F1 completa, F2 simplificada, F3 sustitutiva, R1-R5 rectificativas. */
export type InvoiceKind = 'F1' | 'F2' | 'F3' | 'R1' | 'R2' | 'R3' | 'R4' | 'R5'
/** TipoRectificativa: por sustitución (S) o por diferencias (I). */
export type RectificationType = 'SUBSTITUTION' | 'DIFFERENCES'
export type DocumentStatus = 'DRAFT' | 'CONFIRMED' | 'CONVERTED' | 'CANCELLED'
export type PaymentStatus = 'NOT_APPLICABLE' | 'PENDING' | 'PARTIALLY_PAID' | 'PAID'
export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CONVERTED'

export interface DocumentLine {
  id: string
  order: number
  productId: string | null
  productCode: string | null
  description: string
  quantity: number
  unitPrice: number
  discountPercentage: number
  taxPercentage: number
  taxCodeId: string | null
  taxCode: string | null
  taxCountryCode: string | null
  taxName: string | null
  taxExempt: boolean | null
  netAmount: number
  taxAmount: number
  totalAmount: number
  requestedQuantity: number
  tariffId: string | null
  tariffCode: string | null
  pricingResolvedAmount: number | null
  pricingTraceJson: string | null
}

export interface CommercialDocument {
  id: string
  number: string
  type: DocumentType
  status: DocumentStatus
  customerId: string
  customerCode: string
  customerName: string
  issueDate: string
  dueDate: string | null
  currency: string
  sourceDocumentId: string | null
  paymentMethodId: string | null
  paymentStatus: PaymentStatus
  netAmount: number
  taxAmount: number
  totalAmount: number
  notes: string | null
  lines: DocumentLine[]
  quoteStatus: QuoteStatus | null
  quoteValidUntil: string | null
  quoteDecidedAt: string | null
  quoteRejectionReason: string | null
  customerTaxId: string | null
  customerTaxIdentificationType: TaxIdentificationType | null
  customerTaxCountry: string | null
  invoiceKind: InvoiceKind | null
  rectificationType: RectificationType | null
  rectifiedDocumentId: string | null
  rectifiedNumber: string | null
  rectifiedIssueDate: string | null
  /** Una factura expedida es inmutable: solo se corrige con una rectificativa. */
  issued: boolean
}

export interface CreateDocumentInput {
  type: DocumentType
  customerId: string
  customerCode: string
  customerName: string
  issueDate: string
  dueDate?: string | null
  currency: string
  paymentMethodId?: string | null
  notes?: string | null
  confirm: boolean
  lines: Array<{
    productId?: string | null
    productCode?: string | null
    description: string
    quantity: number
    unitPrice: number
    discountPercentage: number
    taxPercentage: number
    unitPriceOverridden: boolean
    taxPercentageOverridden: boolean
  }>
}

export interface CreateQuoteInput {
  customerId: string
  customerCode: string
  customerName: string
  issueDate: string
  validUntil: string
  currency: string
  paymentMethodId?: string | null
  notes?: string | null
  sendOnCreate: boolean
  lines: CreateDocumentInput['lines']
  numberingSchemeId?: string | null
}

export interface CurrencyDefinition {
  id: string
  code: string
  name: string
  symbol: string
  decimalPlaces: number
  baseCurrency: boolean
  active: boolean
}

export interface PaymentRule {
  installment: number
  dueDays: number
  percentage: number
}

export interface PaymentMethod {
  id: string
  code: string
  name: string
  active: boolean
  rules: PaymentRule[]
}

export interface PaymentMethodInput {
  code: string
  name: string
  rules: Array<{ dueDays: number; percentage: number }>
}

export type DueDateStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED'

export interface DueDate {
  id: string
  documentId: string
  installment: number
  dueDate: string
  amount: number
  paidAmount: number
  status: DueDateStatus
}

export type AuditOutcome = 'SUCCESS' | 'FAILURE' | 'DENIED'

export interface AuditEvent {
  id: string
  eventId: string
  companyId: string
  occurredAt: string
  sourceService: string
  eventType: string
  actorUserId: string | null
  actorName: string | null
  action: string
  resourceType: string
  resourceId: string | null
  outcome: AuditOutcome
  correlationId: string | null
  metadata: Record<string, unknown>
  ingestedAt: string
}

export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export type AlertDeliveryChannel = 'IN_APP'
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
export type AlertConditionOperator = 'EXISTS' | 'NOT_EXISTS' | 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'GREATER_THAN_OR_EQUAL' | 'LESS_THAN' | 'LESS_THAN_OR_EQUAL'

export interface AlertItem {
  id: string
  ruleId: string
  ruleCode: string
  sourceEventId: string
  severity: AlertSeverity
  title: string
  message: string
  status: AlertStatus
  acknowledgedAt: string | null
  acknowledgedBy: string | null
  resolvedAt: string | null
  resolvedBy: string | null
  createdAt: string
  updatedAt: string
}

export interface AlertRule {
  id: string
  code: string
  name: string
  eventType: string
  action: string | null
  resourceType: string | null
  conditionField: string | null
  conditionOperator: AlertConditionOperator | null
  conditionValue: string | null
  severity: AlertSeverity
  titleTemplate: string
  messageTemplate: string
  cooldownMinutes: number
  deliveryChannel: AlertDeliveryChannel
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface AlertRuleInput {
  code: string
  name: string
  eventType: string
  action?: string | null
  resourceType?: string | null
  conditionField?: string | null
  conditionOperator?: AlertConditionOperator | null
  conditionValue?: string | null
  severity: AlertSeverity
  titleTemplate: string
  messageTemplate: string
  cooldownMinutes: number
  deliveryChannel: AlertDeliveryChannel
  active: boolean
}
