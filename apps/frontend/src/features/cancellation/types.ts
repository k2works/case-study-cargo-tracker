/**
 * 輸送中の予約キャンセル（US30・UC22）。
 *
 * **輸送開始前と輸送中で扱いが違う。** 輸送開始前は即時に確定する。輸送中は
 * 貨物が船の上にあり、どこで降ろすかを決めないとキャンセルできないため、
 * 追跡管理者の承認を経る。
 */
export type CancellationStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED'

/** 陸揚げ地の候補（US30-5）。**全港から選ばせない**（[ADR-025] 決定 4）。 */
export type DischargeLocationChoice = {
  unLocode: string
  name: string
  /**
   * なぜ候補なのか。「現在地の港」「次の寄港地」のいずれか。
   *
   * **理由を出す。** 港の名前だけを並べると、追跡管理者はどれを選べばよいか
   * 決められない。
   */
  reason: string
}

/** キャンセル申請の 1 件。 */
export type CancellationRequest = {
  cancellationId: number
  bookingId: string
  reason: string
  status: CancellationStatus
  statusLabel: string
  requestedBy: string
  requestedAt: string
  /** 申請時点の予約状態。**キャンセル料の根拠**になる（US23・IT11）。 */
  bookingStatusAtRequest: string
  bookingStatusAtRequestLabel: string
  /** 承認したときの陸揚げ地。却下・承認前は null。 */
  dischargeLocationUnLocode: string | null
  dischargeLocationName: string | null
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
}

/** 承認待ちの 1 件と、その予約で選べる陸揚げ地。 */
export type PendingCancellation = CancellationRequest & {
  dischargeCandidates: DischargeLocationChoice[]
}

/** キャンセルの申請（US30-1）。理由は必須。 */
export type RequestCancellationRequest = {
  reason: string
}

/** 申請の結果。**輸送開始前は即時に確定する**ため、状態が分かれる。 */
export type CancellationOutcome = {
  request: CancellationRequest
  /** 承認を待つ状態になったか。false なら即時に確定した。 */
  awaitingApproval: boolean
}

/** 承認（US30-5）。**陸揚げ地は必須**。 */
export type ApproveCancellationRequest = {
  dischargeLocationUnLocode: string
  decisionReason: string
}

/** 却下（US30-7）。**理由は必須**で、予約は輸送中のまま維持される。 */
export type RejectCancellationRequest = {
  decisionReason: string
}
