/**
 * 荷役の種別（[ADR-023] 決定 1）。
 *
 * **要件はサーバが答える。** 画面が「積込なら航海番号が要る」と書くと、規則が種別と画面の
 * 2 か所に分かれ、片方だけ直る形になる（IT7 返済枠 0.7 と同じ形）。
 */
export type HandlingTypeChoice = {
  type: string
  /** 画面に出す名前。対訳表を画面に置かない（種別を足したときに直す場所が増える）。 */
  label: string
  requiresVoyageNumber: boolean
  requiresConsigneeConfirmation: boolean
}

/** 作業場所の選択肢（US15-3）。自由入力にすると、綴りの揺れた港が記録に入る。 */
export type HandlingLocation = {
  unLocode: string
  name: string
}

/** 記録された荷役作業（US15・US16）。 */
export type HandlingActivity = {
  id: number
  bookingId: string
  type: string
  locationUnLocode: string
  locationName: string
  completionTime: string
  operatorName: string
  /** 受領・引取では null。 */
  voyageNumber: string | null
  /** 引取以外では null。 */
  consigneeConfirmation: string | null
  /**
   * 予定と違う場所での作業だったか（[ADR-023] 決定 3）。
   *
   * **記録は拒まない。** 現場ではすでに作業が終わっており、拒むと実際に起きたことが
   * どこにも残らない。
   */
  offRoute: boolean
}

/** 荷役作業を記録する要求。 */
export type HandlingActivityRequest = {
  trackingNumber: string
  type: string
  locationUnLocode: string
  /** ISO 8601。業務タイムゾーンで解釈してから送る。 */
  completionTime: string
  voyageNumber: string | null
  consigneeConfirmation: string | null
}
