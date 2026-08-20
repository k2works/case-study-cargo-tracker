import { describe, expect, it } from 'vitest'
import {
  businessLocalToInstant,
  businessToday,
  formatBusinessDate,
  formatBusinessDateTime,
  instantToBusinessLocal,
} from '../business-time'

describe('business-time', () => {
  it('UTC で日付が変わる時刻でも業務タイムゾーンの日付を返す', () => {
    // UTC では 2026-08-18 だが、Asia/Tokyo では 2026-08-19
    const utcLateNight = new Date('2026-08-18T16:30:00Z')

    expect(formatBusinessDate(utcLateNight)).toBe('2026-08-19')
  })

  it('businessToday は YYYY-MM-DD 形式を返す', () => {
    expect(businessToday(new Date('2026-08-19T03:00:00Z'))).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})

describe('業務タイムゾーンでの日時', () => {
  it('画面の入力を業務の暦として解釈して UTC に直す', () => {
    // 日本の 2026-10-01 09:00 は UTC の 00:00
    expect(businessLocalToInstant('2026-10-01T09:00')).toBe('2026-10-01T00:00:00.000Z')
  })

  it('UTC から画面の入力に戻せる（往復して同じになる）', () => {
    expect(instantToBusinessLocal('2026-10-01T00:00:00Z')).toBe('2026-10-01T09:00')
    expect(businessLocalToInstant(instantToBusinessLocal('2026-10-01T00:00:00Z'))).toBe(
      '2026-10-01T00:00:00.000Z',
    )
  })

  it('表示は業務タイムゾーンの日時になる', () => {
    expect(formatBusinessDateTime('2026-10-01T00:00:00Z')).toBe('2026-10-01 09:00')
  })
})
