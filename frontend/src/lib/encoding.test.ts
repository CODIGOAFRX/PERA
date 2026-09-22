import { expect, it } from 'vitest'

const sources = import.meta.glob('../**/*.{ts,tsx,css}', { query: '?raw', import: 'default', eager: true })
it('keeps UI source text free from broken UTF-8 sequences', () => {
  const broken = /\uFFFD|\u00C3[\u0080-\u00BF]|\u00C2[\u0080-\u00BF]|\u00E2\u20AC/
  const failures = Object.entries(sources).filter(([, text]) => broken.test(String(text))).map(([path]) => path)
  expect(failures).toEqual([])
})
