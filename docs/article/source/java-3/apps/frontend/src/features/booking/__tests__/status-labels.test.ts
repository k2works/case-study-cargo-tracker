import { describe, expect, it } from 'vitest'

import {
  BOOKING_STATUS_LABELS,
  bookingStatusLabel,
  ROUTING_STATUS_LABELS,
  routingStatusLabel,
} from '../types'

/**
 * IT11 返済枠 0.7。
 *
 * **網羅そのものは型が守っている**（`Record<BookingStatus, string>`）。ラベルを
 * 書き忘れると `tsc -b` が止まる——`npm run verify` に入っているので、CI と
 * 同じ判定になる。IT10 は画面のキャプチャを撮って初めて 3 つの欠落に気づいた。
 *
 * ここで確かめるのは、型が守れない 2 つである。
 * 1. **バックエンドの列挙と値が揃っているか**（型はフロントの中でしか閉じない）
 * 2. **知らない値が来たときの振る舞い**（型は実行時の値を知らない）
 */
describe('状態の表示名', () => {
  /**
   * バックエンドの `BookingStatus` は 9 値（`BookingStatus.java`。IT12 で `SETTLED` を追加）。
   *
   * **書き写した一覧ではなく、実体（ラベルの鍵）から回す。** 型が網羅を守るので、
   * ここに現れる鍵は必ず `BookingStatus` の全値になる。この検査が守るのは
   * **バックエンドとの一致**であり、増減したときに向こうを見に行く合図になる。
   */
  it('予約の状態は、バックエンドの列挙と同じ 9 値を持つ', () => {
    expect(Object.keys(BOOKING_STATUS_LABELS)).toEqual([
      'PRELIMINARY',
      'ROUTE_PROPOSED',
      'ROUTE_NOTIFIED',
      'CONFIRMED',
      'TRACKING_ISSUED',
      'IN_TRANSIT',
      'DELIVERED',
      'CANCELLED',
      'SETTLED',
    ])
  })

  it('経路の状態は、バックエンドの列挙と同じ 5 値を持つ', () => {
    expect(Object.keys(ROUTING_STATUS_LABELS)).toEqual([
      'NOT_ROUTED',
      'ROUTING_REQUESTED',
      'ROUTED',
      'CONSULTATION_REQUESTED',
      'MISROUTED',
    ])
  })

  it('すべての値が、英字のままではない表示名を持つ', () => {
    for (const [value, label] of Object.entries(BOOKING_STATUS_LABELS)) {
      expect(label, `${value} の表示名が英字のまま。利用者は自分の予約を読めない`)
        .not.toMatch(/^[A-Z_]+$/)
    }
    for (const [value, label] of Object.entries(ROUTING_STATUS_LABELS)) {
      expect(label, `${value} の表示名が英字のまま`).not.toMatch(/^[A-Z_]+$/)
    }
  })

  /**
   * **知らない値はそのまま返す。** サーバが先に新しい状態を返し始めることはありうる
   * （デプロイの順序）。そこで空欄や「不明」を出すと、利用者は自分の予約が消えたと読む。
   */
  it('知らない値が来たら、その値をそのまま出す', () => {
    expect(bookingStatusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(routingStatusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })

  it('知っている値は表示名に置き換える', () => {
    expect(bookingStatusLabel('IN_TRANSIT')).toBe('輸送中')
    // IT12 で足した値。**入金の確認で到達する**（US23-4）
    expect(bookingStatusLabel('SETTLED')).toBe('精算済')
    expect(routingStatusLabel('MISROUTED')).toBe('誤配')
  })
})
