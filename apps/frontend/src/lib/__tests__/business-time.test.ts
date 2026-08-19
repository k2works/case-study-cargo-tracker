import { describe, expect, it } from 'vitest'
import { businessToday, formatBusinessDate } from '../business-time'

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
