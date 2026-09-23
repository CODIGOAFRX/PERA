import { describe, expect, it } from 'vitest'
import { localIsoDate, localIsoDateTime } from './date'

describe('local dates', () => {
  it('uses the local calendar day instead of the UTC one', () => {
    const justAfterMidnight = new Date(2026, 8, 23, 0, 30)
    expect(localIsoDate(justAfterMidnight)).toBe('2026-09-23')
    expect(localIsoDateTime(justAfterMidnight)).toBe('2026-09-23T00:30')
  })

  it('adds calendar days across month and year boundaries', () => {
    expect(localIsoDate(new Date(2026, 11, 20), 30)).toBe('2027-01-19')
    expect(localIsoDate(new Date(2026, 1, 28), 1)).toBe('2026-03-01')
  })
})
