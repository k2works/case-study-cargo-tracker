import type { Estimate } from './estimate-types'
import type { CargoType } from './types'

/**
 * 予約の入力が、見積と食い違っていないか（受入基準 01-7・US04 の未達）。
 *
 * **サーバの `Estimate#differencesFrom` と同じ判定にする。** 画面とサーバで別の
 * 判定を持つと、画面だけが正しく、サーバの誤りを素通りさせる（あるいはその逆）。
 *
 * **断らずに知らせるための材料である。** 条件が変わること自体は業務として普通に
 * 起きる（荷主が数量を増やす）。営業担当者が気づいて荷主に確かめられればよい。
 */
export function differencesFromEstimate(
  estimate: Estimate | undefined,
  booking: {
    originUnLocode: string
    destinationUnLocode: string
    arrivalDeadline: string
    cargoType: CargoType
    weightKg: string
  },
): string[] {
  if (estimate === undefined) {
    return []
  }
  const differences: string[] = []
  if (estimate.originUnLocode !== booking.originUnLocode) {
    differences.push('出発地')
  }
  if (estimate.destinationUnLocode !== booking.destinationUnLocode) {
    differences.push('目的地')
  }
  if (estimate.arrivalDeadline !== booking.arrivalDeadline) {
    differences.push('到着期限')
  }
  if (estimate.cargoType !== booking.cargoType) {
    differences.push('貨物種別')
  }
  // **重量は値で比べる。** 4200 と 4200.000 は同じ重量である
  if (Number(estimate.weightKg) !== Number(booking.weightKg)) {
    differences.push('重量')
  }
  return differences
}
