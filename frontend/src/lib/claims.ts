import type { Claim, ClaimCatalogItem, ClaimCatalogKind } from '../types/api'

type Label = [es: string, en: string]

/** Orden en que se muestran las tablas de clasificación, el mismo que el menú del programa anterior. */
export const claimCatalogKinds: ClaimCatalogKind[] = ['REASON', 'NONCONFORMITY', 'CAUSE', 'AREA', 'RESPONSIBLE', 'RESOLUTION', 'PREVENTIVE_ACTION']

export const claimCatalogLabels: Record<ClaimCatalogKind, Label> = {
  REASON: ['Motivo', 'Reason'],
  NONCONFORMITY: ['No conformidad', 'Nonconformity'],
  CAUSE: ['Causa', 'Cause'],
  AREA: ['Área', 'Area'],
  RESPONSIBLE: ['Responsable', 'Responsible'],
  RESOLUTION: ['Resolución', 'Resolution'],
  PREVENTIVE_ACTION: ['Acción preventiva', 'Preventive action'],
}

export const claimCatalogPlurals: Record<ClaimCatalogKind, Label> = {
  REASON: ['Motivos', 'Reasons'],
  NONCONFORMITY: ['No conformidades', 'Nonconformities'],
  CAUSE: ['Causas', 'Causes'],
  AREA: ['Áreas', 'Areas'],
  RESPONSIBLE: ['Responsables', 'Responsible people'],
  RESOLUTION: ['Resoluciones', 'Resolutions'],
  PREVENTIVE_ACTION: ['Acciones preventivas', 'Preventive actions'],
}

/** Campo de la reclamación que guarda cada clasificación. */
export const claimFieldByKind: Record<ClaimCatalogKind, keyof Pick<Claim, 'reasonId' | 'nonconformityId' | 'causeId' | 'areaId' | 'responsibleId' | 'resolutionId' | 'preventiveActionId'>> = {
  REASON: 'reasonId',
  NONCONFORMITY: 'nonconformityId',
  CAUSE: 'causeId',
  AREA: 'areaId',
  RESPONSIBLE: 'responsibleId',
  RESOLUTION: 'resolutionId',
  PREVENTIVE_ACTION: 'preventiveActionId',
}

export const pickLabel = (label: Label, language: string) => label[language === 'es' ? 0 : 1]

export function catalogName(items: ClaimCatalogItem[], id: string | null) {
  return id ? items.find((item) => item.id === id)?.name ?? '—' : '—'
}
