/** Motivos de las entradas y retiradas registradas desde el almacén (mismos códigos que el servidor). */
export type StockReason = 'RECEIPT' | 'CUSTOMER_RETURN' | 'COUNT' | 'DAMAGED' | 'EXPIRED' | 'SPOILED' | 'LOST' | 'INTERNAL_USE' | 'OTHER'

export const REASONS: Record<'IN' | 'OUT', StockReason[]> = {
  IN: ['RECEIPT', 'CUSTOMER_RETURN', 'COUNT', 'OTHER'],
  OUT: ['DAMAGED', 'EXPIRED', 'SPOILED', 'LOST', 'INTERNAL_USE', 'COUNT', 'OTHER'],
}

export const reasonLabels: Record<StockReason, [string, string]> = {
  RECEIPT: ['Recepción de mercancía', 'Goods received'],
  CUSTOMER_RETURN: ['Devolución de cliente', 'Customer return'],
  COUNT: ['Recuento / inventario', 'Stock count'],
  DAMAGED: ['Roto o dañado', 'Broken or damaged'],
  EXPIRED: ['Caducado', 'Expired'],
  SPOILED: ['En mal estado', 'Spoiled'],
  LOST: ['Pérdida o robo', 'Lost or stolen'],
  INTERNAL_USE: ['Consumo interno', 'Internal use'],
  OTHER: ['Otro motivo', 'Other'],
}

export function reasonLabel(reason: string | null | undefined, language: 'es' | 'en') {
  return reason && reason in reasonLabels ? reasonLabels[reason as StockReason][language === 'es' ? 0 : 1] : ''
}
