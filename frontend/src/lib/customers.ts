import type { CustomerCatalogItem, CustomerCatalogKind, Salesperson } from '../types/api'

type Label = [es: string, en: string]

/** Tablas con las que se clasifica a los clientes, en el orden de la ficha. */
export const customerCatalogKinds: CustomerCatalogKind[] = ['GROUP', 'TYPE', 'DELIVERY_METHOD', 'INACTIVE_REASON']

export const customerCatalogLabels: Record<CustomerCatalogKind, Label> = {
  GROUP: ['Grupo', 'Group'],
  TYPE: ['Tipo de cliente', 'Customer type'],
  DELIVERY_METHOD: ['Forma de entrega', 'Delivery method'],
  INACTIVE_REASON: ['Motivo de baja', 'Discharge reason'],
}

export const customerCatalogPlurals: Record<CustomerCatalogKind, Label> = {
  GROUP: ['Grupos', 'Groups'],
  TYPE: ['Tipos de cliente', 'Customer types'],
  DELIVERY_METHOD: ['Formas de entrega', 'Delivery methods'],
  INACTIVE_REASON: ['Motivos de baja', 'Discharge reasons'],
}

export const pickLabel = (label: Label, language: string) => label[language === 'es' ? 0 : 1]

export const catalogItemName = (items: CustomerCatalogItem[], id: string | null | undefined) =>
  id ? items.find((item) => item.id === id)?.name ?? '—' : '—'

export const salespersonName = (people: Salesperson[], id: string | null | undefined) =>
  id ? people.find((person) => person.id === id)?.name ?? '—' : '—'

/** Opciones de un desplegable: las activas y, si el cliente ya tiene una dada de baja, también esa. */
export function selectableItems<T extends { id: string; active: boolean }>(items: T[], currentId: string | null | undefined) {
  return items.filter((item) => item.active || item.id === currentId)
}
