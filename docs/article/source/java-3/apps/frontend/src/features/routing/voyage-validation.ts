import type { MovementInput, RoutingCargoType } from './types'

/** 登録・訂正の途中にある航海。まだサーバへ送れる形ではない。 */
export type VoyageDraft = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  supportedCargoTypes: RoutingCargoType[]
  movements: MovementInput[]
}

/**
 * 送信前に、サーバが返すのと同じ文言で拒む。
 *
 * ブラウザ既定の検証は吹き出しで知らせるだけで画面には何も残らない。
 * 「押しても何も起きない」と受け取られ、経路設計者は原因を探せない。
 */
export function voyageInvalidMessage({
  voyageNumber,
  vesselName,
  carrierName,
  supportedCargoTypes,
  movements,
}: VoyageDraft): string | null {
  if (voyageNumber.trim() === '') return '航海番号は必須です'
  if (vesselName.trim() === '') return '船名は必須です'
  if (carrierName.trim() === '') return '運送会社は必須です'
  if (supportedCargoTypes.length === 0) return '対応できる貨物種別を 1 つ以上選んでください'
  if (movements.length === 0) return '寄港地を 1 区間以上入力してください'

  for (const [index, movement] of movements.entries()) {
    const message = movementInvalidMessage(movement, index, movements[index - 1])
    if (message !== null) return message
  }
  return null
}

/** 区間 1 つ分の検査。前の区間との繋がりもここで見る。 */
function movementInvalidMessage(
  movement: MovementInput,
  index: number,
  previous: MovementInput | undefined,
): string | null {
  const label = `${index + 1} 区間目`
  if (movement.departureUnLocode === '') return `${label}の出発地を選んでください`
  if (movement.arrivalUnLocode === '') return `${label}の到着地を選んでください`
  if (movement.departureUnLocode === movement.arrivalUnLocode) {
    return `${label}の出発地と到着地は同じにできません`
  }
  if (movement.departureTime === '') return `${label}の出発日時を入力してください`
  if (movement.arrivalTime === '') return `${label}の到着日時を入力してください`
  if (movement.arrivalTime <= movement.departureTime) {
    return `${label}の到着日時は出発日時より後にしてください`
  }
  if (previous === undefined) return null
  // つながっていない区間の並びは「航海」ではない。経路候補算出が実在しない乗り継ぎを提案する
  if (previous.arrivalUnLocode !== movement.departureUnLocode) {
    return `${label}は、前の区間の到着地から出発するようにしてください`
  }
  if (movement.departureTime < previous.arrivalTime) {
    return `${label}が前の区間の到着より前に出発しています`
  }
  return null
}
