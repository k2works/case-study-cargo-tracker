/**
 * 通関申告（US29・UC21）。
 *
 * **状態の読み方はサーバが答える。** 画面に対訳表を置くと、状態を足したときに
 * 画面が列挙の名前をそのまま出す（[ADR-023] 決定 1 と同じ形）。
 */
export type CustomsStatusChoice = {
  status: string
  /** 画面に出す名前。 */
  label: string
}

/** 通関申告の 1 件（一覧・詳細で共通）。 */
export type CustomsDeclaration = {
  declarationId: number
  declarationNumber: string
  bookingId: string
  trackingNumber: string
  /** 申告日時。**業務のタイムゾーンで整形済み**——画面で UTC を直さない。 */
  declaredAt: string
  status: string
  statusLabel: string
  /** 通関完了日時。まだなら null。 */
  clearedAt: string | null
  /**
   * 留置のまま 3 日を超えているか（US29-6）。
   *
   * **判定はサーバが業務タイムゾーンの Clock で行う。** 画面で日付を引き算すると、
   * 利用者の端末の時計と時差の分だけ結果が変わる。
   */
  heldOverdue: boolean
  /** 留置になってからの経過日数。留置でなければ null。 */
  heldDays: number | null
  remarks: string | null
}

/** 状態変更の履歴 1 件（US29-8）。**理由は必須**であり、null にならない。 */
export type CustomsStatusChange = {
  fromStatus: string
  fromStatusLabel: string
  toStatus: string
  toStatusLabel: string
  changedBy: string
  changedAt: string
  reason: string
}

/** 申告詳細。履歴を伴う。 */
export type CustomsDeclarationDetail = CustomsDeclaration & {
  history: CustomsStatusChange[]
}

/** 申告の登録（US29-1）。初期状態はサーバが決める（PENDING）。 */
export type RegisterCustomsDeclarationRequest = {
  trackingNumber: string
  declarationNumber: string
  declaredAt: string
  remarks: string | null
}

/** 状態の更新（US29-2）。**理由は必須**。 */
export type UpdateCustomsStatusRequest = {
  status: string
  reason: string
}

/** 一覧の検索条件（US29-7）。 */
export type CustomsSearchCriteria = {
  bookingId: string
  trackingNumber: string
  status: string
}

/** 留置 3 日超の件数（横断規約）。**件数から対象一覧へ辿れること**。 */
export type OverdueCustomsSummary = {
  count: number
}
