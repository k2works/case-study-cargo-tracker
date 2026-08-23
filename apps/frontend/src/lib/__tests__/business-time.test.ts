import { describe, expect, it } from 'vitest'
import {
  InvalidBusinessDateTimeError,
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

/**
 * 読めない日時で例外を投げるが、**型で区別できるようにする**。
 *
 * 素の `RangeError` のままだと、呼び出し側は想定外の不具合と区別できず、
 * 送信そのものが止まって画面には何も出ない。利用者からは「押しても何も起きない」に見える。
 */
describe('読めない日時', () => {
  it('空文字は、区別できる誤りとして投げる', () => {
    expect(() => businessLocalToInstant('')).toThrow(InvalidBusinessDateTimeError)
  })

  it('日時として読めない文字列も同じ', () => {
    expect(() => businessLocalToInstant('きのう')).toThrow(InvalidBusinessDateTimeError)
  })

  it('読める日時は、これまでどおり変換する', () => {
    expect(businessLocalToInstant('2027-09-02T09:00')).toBe('2027-09-02T00:00:00.000Z')
  })
})

/**
 * **`Date` の解析に頼らない。**
 *
 * `new Date(':00Z')` は例外にならず **2000 年**として読まれる。空欄のまま送ると
 * 「2000-01-01 の作業」が記録される——例外より悪い壊れ方である。
 */
describe('日時の解析に頼らない', () => {
  it('空文字を 2000 年として通さない', () => {
    expect(new Date(':00Z').getFullYear(), '前提が変わった').toBe(2000)
    expect(() => businessLocalToInstant('')).toThrow(InvalidBusinessDateTimeError)
  })

  it('存在しない日は断る', () => {
    expect(() => businessLocalToInstant('2027-02-30T09:00')).toThrow(
      InvalidBusinessDateTimeError,
    )
  })
})
