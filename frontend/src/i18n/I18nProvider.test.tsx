import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { LanguageSelector } from '../components/LanguageSelector'
import { I18nProvider, useTranslation } from './I18nProvider'
import { LANGUAGE_STORAGE_KEY } from './language'

function Probe() {
  const { t } = useTranslation()
  return <span>{t('dashboard.title')}</span>
}

describe('I18nProvider', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.lang = 'es'
  })

  it('switches language, persists it and updates the document language', () => {
    render(<I18nProvider><LanguageSelector /><Probe /></I18nProvider>)
    fireEvent.change(screen.getByRole('combobox', { name: 'Idioma' }), { target: { value: 'en' } })

    expect(screen.getByText('Business overview')).toBeInTheDocument()
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
