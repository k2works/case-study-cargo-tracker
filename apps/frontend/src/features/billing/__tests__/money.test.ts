import { describe, expect, it } from 'vitest'

import { formatRate, formatYen, roundYen } from '../money'

/**
 * 金額と率の整形（[ADR-027] 決定 2）。
 *
 * <p><strong>ここでは計算しない。</strong>丸めはサーバが済ませており、画面は返ってきた
 * 値をそのまま出す。フロントで丸めると丸める場所が 2 か所になり、画面と保存値が食い違う。
 */
describe('金額の表示', () => {
  it('桁区切りを入れて円で出す', () => {
    expect(formatYen({ value: 1_260_000, currency: 'JPY' })).toBe('¥1,260,000')
  })

  /** 減額は負の数で入る（決定 6）。**符号を落とすと、減額が加算に見える。** */
  it('負の金額は符号を先に出す', () => {
    expect(formatYen({ value: -10_000, currency: 'JPY' })).toBe('-¥10,000')
  })

  /** **無いことと 0 は違う。** キャンセル料が無い予約に ¥0 を出すと、0 円が算定されたと読める。 */
  it('金額が無ければ「—」を出す', () => {
    expect(formatYen(null)).toBe('—')
    expect(formatYen(undefined)).toBe('—')
  })

  it('0 円は 0 円として出す', () => {
    expect(formatYen({ value: 0, currency: 'JPY' })).toBe('¥0')
  })
})

/**
 * IT11 レビュー 高 2。**サーバと同じ向きに丸める。**
 *
 * <p>画面が `Math.round` を使うと、サーバの `HALF_UP`（0 から遠いほうへ丸める）と
 * 向きが違う——**小計が負になる調整**（大幅な減額・補償）で 1 円ずれる。
 * 金額を扱う画面で最も信頼を失う種類の食い違いであり、担当者は「どちらが本当の
 * 請求額か」を確かめる手段を持たない。
 */
describe('円への丸め', () => {
  it('正の端数は四捨五入する', () => {
    expect(roundYen(100.4)).toBe(100)
    expect(roundYen(100.5)).toBe(101)
    expect(roundYen(100.6)).toBe(101)
  })

  /**
   * **負の端数は 0 から遠いほうへ丸める**（サーバの HALF_UP と同じ）。
   *
   * `Math.round(-1.5)` は -1 を返す（+∞ 方向）。サーバは -2 を返す。
   */
  it('負の端数は 0 から遠いほうへ丸める', () => {
    expect(roundYen(-1.5), 'Math.round のままだと -1 になり、サーバと 1 円ずれる').toBe(-2)
    expect(roundYen(-2.5)).toBe(-3)
    expect(roundYen(-1.4)).toBe(-1)
    expect(roundYen(-1.6)).toBe(-2)
  })

  it('端数が無ければそのまま', () => {
    expect(roundYen(0)).toBe(0)
    expect(roundYen(-100)).toBe(-100)
    expect(roundYen(378000)).toBe(378000)
  })
})

describe('率の表示', () => {
  /** **率そのものを出す**（22-4）。額だけでは率を復元できない——割り戻すと丸めの分ずれる。 */
  it('割合を百分率で出す', () => {
    expect(formatRate(0.1)).toBe('10%')
    expect(formatRate(0.3)).toBe('30%')
  })

  it('端数のある率は小数第 1 位まで出す', () => {
    expect(formatRate(0.155)).toBe('15.5%')
  })

  /**
   * **契約が無いことと 0% は違う**（[ADR-012] と同じ判断）。
   *
   * 0% を出すと「割引が 0 だった」に読め、契約が無いことと区別できない。
   */
  it('率が無ければ「—」を出す', () => {
    expect(formatRate(null)).toBe('—')
  })

  it('0% は 0% として出す', () => {
    expect(formatRate(0)).toBe('0%')
  })
})
