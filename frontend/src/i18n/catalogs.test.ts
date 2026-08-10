import { describe, expect, it } from 'vitest'
import { en, es, translate } from './catalogs'

describe('translation catalogues', () => {
  it('keeps exact key parity between Spanish and English', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(es).sort())
  })

  it('interpolates stable translation keys', () => {
    expect(translate('es', 'common.records', { count: 3 })).toBe('3 registros')
    expect(translate('en', 'common.records', { count: 3 })).toBe('3 records')
  })
})
