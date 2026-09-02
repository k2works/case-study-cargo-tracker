/**
 * 輸送見積（US01）。
 *
 * **見積は予約ではない。** 作っても貨物は動かない。それでも荷主に出す数字であり、
 * **実料金と違ってはならない**（[ADR-028] 決定 6——式は billingms の 1 か所にある）。
 */
import type { CargoType } from './types'

/**
 * ルート候補（受入基準 01-3）。
 *
 * **4 項目を持つ**——航海番号・経由港・所要日数・概算料金。**1 つ欠けても字面は満たす**
 * ので、画面のテストでは 1 項目ずつ突き合わせる（IT11 Try 2）。
 */
export type RouteCandidate = {
  voyageNumber: string
  /** 経由港（UN/LOCODE）。**直行なら `null`**。 */
  transitPort: string | null
  transitDays: number
  estimatedCost: number
}

export type Estimate = {
  /** 識別子（UUID）。**URL に出る**——推測できないことに意味がある。 */
  estimateId: string
  /** 見積番号。**荷主と電話で読み合わせる**（受入基準 01-4）。 */
  estimateNumber: string
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: CargoType
  weightKg: number
  status: string
  statusLabel: string
  candidates: RouteCandidate[]
}

/**
 * 候補の試算結果（受入基準 01-2・01-3・01-5）。
 *
 * **「候補が 0 件」と「間に合う候補が 0 件」を区別する。** 後者は「最短でも N 日超過
 * します」と言える——荷主に折り返す言葉があるかどうかが違う。
 */
export type EstimateQuote = {
  candidates: RouteCandidate[]
  /** 間に合う候補が無いとき、最短でも何日超過するか。あれば `null`。 */
  daysExceeded: number | null
}

/** 見積の依頼（受入基準 01-1）。入力は 5 項目。 */
export type CreateEstimateRequest = {
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: CargoType
  weightKg: number
}
