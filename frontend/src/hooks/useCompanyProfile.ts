import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { apiDownload, apiFetch } from '../lib/api'
import { blobToDataUrl } from '../lib/print'

/** Datos de la empresa que se imprimen en tickets y etiquetas. */
export interface CompanyProfile {
  displayName: string
  legalName: string | null
  taxId: string | null
  addressLine1: string | null
  postalCode: string | null
  city: string | null
  phone: string | null
  website: string | null
  baseCurrency: string
  /** Logo como data URL (vacío si la empresa no tiene logo). */
  logo: string
}

interface SettingsResponse extends Omit<CompanyProfile, 'logo'> { logoStorageKey: string | null; logoSha256: string | null }

const cache = new Map<string, Promise<CompanyProfile>>()

export function useCompanyProfile(): CompanyProfile {
  const { company } = useAuth()
  const fallback: CompanyProfile = { displayName: company?.name ?? 'PERA ERP', legalName: null, taxId: null, addressLine1: null, postalCode: null, city: null, phone: null, website: null, baseCurrency: 'EUR', logo: '' }
  const [profile, setProfile] = useState<CompanyProfile>(fallback)
  useEffect(() => {
    const key = company?.id ?? 'current'
    let promise = cache.get(key)
    if (!promise) {
      promise = apiFetch<SettingsResponse>('/api/v1/company-settings/current').then(async (settings) => {
        let logo = ''
        if (settings.logoStorageKey) {
          try { logo = await blobToDataUrl((await apiDownload('/api/v1/company-settings/current/logo')).blob) } catch { logo = '' }
        }
        return {
          displayName: settings.displayName || company?.name || 'PERA ERP', legalName: settings.legalName ?? null, taxId: settings.taxId ?? null,
          addressLine1: settings.addressLine1, postalCode: settings.postalCode, city: settings.city, phone: settings.phone,
          website: settings.website, baseCurrency: settings.baseCurrency || 'EUR', logo,
        }
      })
      // Un fallo no se guarda: la siguiente pantalla volverá a intentarlo.
      promise.catch(() => cache.delete(key))
      cache.set(key, promise)
    }
    let active = true
    promise.then((value) => { if (active) setProfile(value) }).catch(() => undefined)
    return () => { active = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [company?.id])
  return profile
}
