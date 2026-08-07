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

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  violations?: Record<string, string>
}

export type RiskPolicy = 'WARN' | 'REQUIRE_CONFIRMATION' | 'BLOCK'

export interface Customer {
  id: string
  partyId: string
  code: string
  legalName: string
  tradeName: string | null
  taxId: string | null
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
}

export interface CustomerInput {
  code: string
  legalName: string
  tradeName?: string | null
  taxId?: string | null
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
  familyId: string | null
  categoryId: string | null
  unitOfMeasure: UnitOfMeasure
  basePrice: number
  taxRate: number
  active: boolean
}

export interface ProductInput {
  code: string
  name: string
  description?: string | null
  productTypeId?: string | null
  familyId?: string | null
  categoryId?: string | null
  unitOfMeasure: UnitOfMeasure
  basePrice: number
  taxRate: number
  active: boolean
}

export type DocumentType = 'QUOTE' | 'DELIVERY_NOTE' | 'INVOICE' | 'WORK_ORDER'
export type DocumentStatus = 'DRAFT' | 'CONFIRMED' | 'CONVERTED' | 'CANCELLED'
export type PaymentStatus = 'NOT_APPLICABLE' | 'PENDING' | 'PARTIALLY_PAID' | 'PAID'

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
  netAmount: number
  taxAmount: number
  totalAmount: number
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
  }>
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
