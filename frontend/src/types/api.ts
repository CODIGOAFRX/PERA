export interface ContactDetails {
  addressLine1?: string | null
  city?: string | null
  region?: string | null
  postalCode?: string | null
  countryCode?: string | null
  iban?: string | null
  bankAccountHolder?: string | null
}

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
  details?: ContactDetails | null
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
  classification?: CustomerClassification | null
}

/** Clasificación comercial del cliente. Si no se envía al modificar, se conserva la guardada. */
export interface CustomerClassification {
  groupId: string | null
  typeId: string | null
  salespersonId: string | null
  deliveryMethodId: string | null
  inactiveReasonId: string | null
  mobile: string | null
  accountingAccount: string | null
}

export type CustomerCatalogKind = 'GROUP' | 'TYPE' | 'DELIVERY_METHOD' | 'INACTIVE_REASON'

export interface CustomerCatalogItem {
  id: string
  kind: CustomerCatalogKind
  name: string
  active: boolean
}

export interface Salesperson {
  id: string
  code: string
  name: string
  email: string | null
  phone: string | null
  commissionPercentage: number | null
  active: boolean
}

export interface CustomerContact {
  id: string
  name: string
  position: string | null
  phone: string | null
  mobile: string | null
  email: string | null
  notes: string | null
  primaryContact: boolean
}

export interface CustomerAddress {
  id: string
  type: string
  label: string | null
  line1: string
  line2: string | null
  postalCode: string | null
  city: string | null
  province: string | null
  country: string | null
  contactPhone: string | null
  primaryAddress: boolean
  active: boolean
}

export interface CustomerNote {
  id: string
  title: string
  message: string
  showOnDocuments: boolean
  createdAt: string
  updatedAt: string
}

export interface CustomerInput {
  details?: ContactDetails | null
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
  classification?: CustomerClassification | null
}

export interface Supplier {
  details?: ContactDetails | null
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
  details?: ContactDetails | null
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
  /** Código y texto de la AEAT, o el motivo por el que no se pudo remitir. */
  aeatErrorCode?: string | null
  aeatMessage?: string | null
  attemptCount?: number
  lastAttemptAt?: string | null
}

/** Situación de la remisión automática a la AEAT de la empresa. */
export interface VerifactuRemissionSummary {
  enabled: boolean
  environment: 'TEST' | 'PRODUCTION'
  connectionConfigured: boolean
  connectionActive: boolean
  nextSendAt: string | null
  lastSentAt: string | null
  failures: number
  lastError: string | null
  counts: Record<VerifactuState, number>
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

/** Página plana que devuelve operations-service (compras, inventario, logística). */
export interface FlatPage<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Warehouse {
  id: string
  code: string
  name: string
  location: string | null
  defaultWarehouse: boolean
  active: boolean
}

export interface StockLevel {
  id: string
  warehouseId: string
  productId: string
  productCode: string
  productName: string
  unitOfMeasure: string
  quantity: number
  updatedAt: string
}

export type StockMovementType = 'PURCHASE_RECEIPT' | 'PURCHASE_REVERSAL' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'SALES_ISSUE' | 'SALES_RETURN'

export interface StockMovement {
  id: string
  warehouseId: string
  productId: string
  productCode: string
  productName: string
  unitOfMeasure: string
  type: StockMovementType
  quantity: number
  balanceAfter: number
  unitCost: number | null
  costCurrencyCode: string | null
  occurredAt: string
  sourceType: 'MANUAL' | 'TRANSFER' | 'PURCHASE_DOCUMENT' | 'SALES_DOCUMENT'
  sourceId: string | null
  sourceNumber: string | null
  note: string | null
}

export type SalesDeliveryStatus = 'PENDING' | 'POSTED' | 'NOT_APPLICABLE' | 'REVERSED' | 'DISMISSED'

/** Albarán o factura de venta y el estado de su salida de almacén. */
export interface SalesDelivery {
  id: string
  sourceDocumentId: string
  sourceType: string
  sourceNumber: string
  sourceDate: string
  sourceStatus: string
  customerCode: string | null
  customerName: string
  status: SalesDeliveryStatus
  warehouseId: string | null
  problem: string | null
  postedAt: string | null
  lines: Array<{ sequence: number; productId: string; productCode: string; description: string; quantity: number }>
}

export type PurchaseDocumentType = 'PURCHASE_ORDER' | 'GOODS_RECEIPT' | 'SUPPLIER_INVOICE'

export interface PurchaseLine {
  id: string
  sequence: number
  productId: string | null
  productCode: string | null
  description: string
  unitOfMeasure: string
  quantity: number
  unitPrice: number
  discountPercentage: number
  taxPercentage: number
  netAmount: number
}

export interface PurchaseDocument {
  id: string
  type: PurchaseDocumentType
  number: string
  status: DocumentStatus
  supplierId: string
  supplierCode: string
  supplierName: string
  supplierTaxId: string | null
  supplierReference: string | null
  issueDate: string
  expectedDate: string | null
  warehouseId: string | null
  currencyCode: string
  sourceDocumentId: string | null
  stockReceivedUpstream: boolean
  stockPosted: boolean
  netAmount: number
  taxAmount: number
  totalAmount: number
  notes: string | null
  lines: PurchaseLine[]
}

export interface PurchaseDocumentInput {
  type: PurchaseDocumentType
  supplierId: string
  supplierCode: string
  supplierName: string
  supplierTaxId?: string | null
  supplierReference?: string | null
  issueDate: string
  expectedDate?: string | null
  warehouseId?: string | null
  currencyCode: string
  notes?: string | null
  lines: Array<{
    productId?: string | null
    productCode?: string | null
    description: string
    unitOfMeasure: string
    quantity: number
    unitPrice: number
    discountPercentage: number
    taxPercentage: number
  }>
}

export type ReceiptStatus = 'PENDING' | 'REMITTED' | 'COLLECTED' | 'RETURNED' | 'CANCELLED'
export type CollectionMethod = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'DIRECT_DEBIT' | 'CHEQUE' | 'OTHER'

/** Recibo de cobro de un vencimiento de factura. */
export interface Receipt {
  id: string
  receiptNumber: string
  customerId: string
  customerCode: string | null
  customerName: string
  documentId: string
  documentNumber: string
  installment: number
  amount: number
  currencyCode: string
  dueDate: string
  status: ReceiptStatus
  collectionDate: string | null
  collectionMethod: CollectionMethod | null
  returnDate: string | null
  returnReason: string | null
  remittanceId: string | null
  notes: string | null
}

/** `invoiceUpdated` es falso cuando la cartera se actualizó pero la factura en Ventas no. */
export interface ReceiptOperation {
  receipts: Receipt[]
  invoiceUpdated: boolean
}

export type RemittanceStatus = 'DRAFT' | 'SENT' | 'SETTLED' | 'PARTIALLY_RETURNED' | 'CANCELLED'

export interface Remittance {
  id: string
  remittanceNumber: string
  bankAccount: string
  currencyCode: string
  creationDate: string
  sentDate: string | null
  settlementDate: string | null
  status: RemittanceStatus
  totalAmount: number
  notes: string | null
  receipts: Receipt[]
  invoiceUpdated: boolean | null
}

export interface CashRegister {
  id: string
  code: string
  name: string
  ownerName: string | null
  active: boolean
  openSessionId: string | null
}

export type CashMovementType = 'OPENING' | 'SALE_COLLECTION' | 'INCOME' | 'EXPENSE' | 'WITHDRAWAL' | 'CLOSING_ADJUSTMENT'

export interface CashMovement {
  id: string
  occurredAt: string
  type: CashMovementType
  amount: number
  signedAmount: number
  receiptId: string | null
  concept: string
}

export interface CashSession {
  id: string
  cashRegisterId: string
  status: 'OPEN' | 'CLOSED'
  openedAt: string
  closedAt: string | null
  openingAmount: number
  balance: number
  expectedClosingAmount: number | null
  actualClosingAmount: number | null
  difference: number | null
  closingNote: string | null
  movements: CashMovement[]
}

export type ClaimCatalogKind = 'REASON' | 'NONCONFORMITY' | 'CAUSE' | 'AREA' | 'RESPONSIBLE' | 'RESOLUTION' | 'PREVENTIVE_ACTION'

export interface ClaimCatalogItem {
  id: string
  kind: ClaimCatalogKind
  name: string
  followUpDays: number | null
  active: boolean
}

export type ClaimStatus = 'OPEN' | 'CLOSED'

export interface ClaimLine {
  sequence: number
  productId: string | null
  productCode: string | null
  description: string
  quantity: number
}

export interface ClaimComment {
  id: string
  authorName: string
  text: string
  createdAt: string
}

/** Reclamación de cliente. Cada clasificación apunta a un elemento de su tabla. */
export interface Claim {
  id: string
  number: string
  claimDate: string
  status: ClaimStatus
  customerId: string
  customerCode: string | null
  customerName: string
  sourceDocumentId: string | null
  sourceDocumentNumber: string | null
  sourceDocumentDate: string | null
  description: string
  reportedByName: string
  reasonId: string | null
  nonconformityId: string | null
  causeId: string | null
  areaId: string | null
  responsibleId: string | null
  resolutionId: string | null
  preventiveActionId: string | null
  followUpDate: string | null
  overdue: boolean
  closedOn: string | null
  closingNote: string | null
  daysOpen: number
  lines: ClaimLine[]
  comments: ClaimComment[]
}

export interface AgendaEntryType {
  id: string
  name: string
  color: string | null
  active: boolean
}

export interface Contact {
  id: string
  name: string
  organization: string | null
  phone: string | null
  mobile: string | null
  email: string | null
  address: string | null
  postalCode: string | null
  city: string | null
  region: string | null
  customerId: string | null
  customerName: string | null
  notes: string | null
  active: boolean
}

export type AgendaEntryStatus = 'PENDING' | 'DONE' | 'CANCELLED'

/** Cita de la agenda. Sin hora de inicio es de todo el día. */
export interface AgendaEntry {
  id: string
  date: string
  startTime: string | null
  endTime: string | null
  title: string
  details: string | null
  typeId: string | null
  assigneeName: string | null
  customerId: string | null
  customerName: string | null
  contactId: string | null
  contactName: string | null
  contactPhone: string | null
  location: string | null
  documentReference: string | null
  status: AgendaEntryStatus
  completedOn: string | null
  outcome: string | null
  createdByName: string
}
