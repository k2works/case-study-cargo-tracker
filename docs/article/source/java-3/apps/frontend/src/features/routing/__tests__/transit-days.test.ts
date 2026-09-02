import { describe, expect, it } from 'vitest'
import { transitDaysBetween } from '../transit-days'

/**
 * 所要日数の規則は 1 つである。
 *
 * 予約詳細は四捨五入して下限を 1 日にしており、サーバが返す経路候補の日数と 1 日ずれていた。
 * 営業が荷主に伝える日数と経路設計者が見ている日数が違うと、どちらが正しいのかを確かめる
 * 手段が現場に無い。
 */
describe('所要日数', () => {
  it('日単位で切り捨てる（サーバの ChronoUnit.DAYS.between と同じ）', () => {
    // 13 日と 12 時間。四捨五入なら 14 日になる
    expect(
      transitDaysBetween('2027-09-02T09:00:00Z', '2027-09-15T21:00:00Z'),
    ).toBe(13)
  })

  it('日をまたがなければ 0 日。画面だけが 1 日と言わない', () => {
    expect(
      transitDaysBetween('2027-09-02T09:00:00Z', '2027-09-02T23:00:00Z'),
    ).toBe(0)
  })
})
