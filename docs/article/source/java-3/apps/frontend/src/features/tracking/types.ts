/** 追跡の状況（`TrackingStatus`）。値はサーバの列挙と一致させる。 */
export type TrackingStatus =
  | "NOT_RECEIVED"
  | "RECEIVED"
  | "LOADED"
  | "ONBOARD_CARRIER"
  | "UNLOADED"
  | "AWAITING_CLAIM"
  | "CLAIMED"
  | "EXCEPTION"
  | "UNKNOWN";

/** 例外の種別（`ExceptionType`）。**起票できるのは 3 つだけ**（[ADR-024] 決定 11）。 */
export type ExceptionType =
  "DELAY" | "DAMAGE" | "LOST" | "MISROUTE" | "CUSTOMS_HOLD";

/** 追跡の 1 件の出来事。荷役の記録と手動更新の両方が並ぶ（US18-3）。 */
export type TrackingEvent = {
  occurredAt: string;
  status: TrackingStatus;
  /** 表示名。**画面が対訳表を持たない**（[ADR-023] 決定 1 と同じ形）。 */
  statusLabel: string;
  locationName: string;
};

/** 通知したという事実（[ADR-024] 決定 9）。**メールは送っていない**。 */
export type TrackingNotice = {
  noticedAt: string;
  message: string;
};

/**
 * 公開の追跡照会が返すもの（[ADR-024] 決定 5）。
 *
 * **返さないものを型に持たない。** 予約番号・荷主名・作業者・航海番号・例外の詳細は
 * ここに現れない。認証が無い以上、追跡番号を手に入れた誰もが見る。
 */
export type PublicTracking = {
  trackingNumber: string;
  status: TrackingStatus;
  statusLabel: string;
  /** 現在地の港湾名。まだ動いていなければ出発港。 */
  locationName: string;
  /** 推定到着日。**決まっていなければ null**——0 や今日で埋めない（US18-2）。 */
  estimatedArrival: string | null;
  /** 例外が起きているか。詳細は返さない。 */
  hasException: boolean;
  /** 紛失だけが緊急（[ADR-024] 決定 3）。 */
  urgent: boolean;
  events: TrackingEvent[];
  notices: TrackingNotice[];
};

/** 荷主向けの追跡一覧 1 件。自社貨物だけを返す（US33）。 */
export type ShipperTrackingSummary = {
  trackingNumber: string;
  status: TrackingStatus;
  statusLabel: string;
  locationName: string;
  estimatedArrival: string | null;
  hasException: boolean;
  urgent: boolean;
};

/**
 * 荷主向けの追跡一覧。
 *
 * 認証は通っていても利用者と荷主が紐付いていないことがある。空配列だけを返すと
 * 「貨物が無い」のか「紐付けが無い」のか分からないため、linked を明示する。
 */
export type ShipperTrackingList = {
  linked: boolean;
  contactMessage: string | null;
  cargos: ShipperTrackingSummary[];
};

/** 荷主向け詳細。公開照会より強い自社境界の内側で、経過まで返す。 */
export type ShipperTrackingDetail = ShipperTrackingSummary & {
  events: TrackingEvent[];
  /**
   * 過去のお知らせ（US39・IT16 レビュー 高 3）。
   *
   * **ポップアップは出した時点で既読になる。**読み直せる場所が無いと、
   * 回線が切れた・タブを閉じた・見落とした荷主はその知らせに二度と到達できない。
   */
  notices?: ShipperTrackingNotice[];
};

/** 荷主が読み直すお知らせ 1 件。 */
export type ShipperTrackingNotice = {
  /** 通知の時刻（業務タイムゾーン）。**いつの話かが要る**。 */
  noticedAt: string;
  message: string;
};

/** 追跡管理者が見る 1 件。公開照会より多くを返す。 */
export type ManagedTracking = {
  trackingNumber: string;
  bookingId: string;
  status: TrackingStatus;
  statusLabel: string;
  locationName: string;
  estimatedArrival: string | null;
  activeException: TrackingException | null;
  events: TrackingEvent[];
  /**
   * 起きた例外の記録（US19-5）。**解決したものも含む**。
   *
   * 「先週の遅れはどうなったのか」と荷主から問い合わせが来たとき、担当者はこれを読む。
   * 解決したら見えなくなる、では業務が回らない。
   */
  exceptionHistory: ResolvedException[];
};

/** 起きた例外の 1 件（解決済みを含む）。 */
export type ResolvedException = {
  exceptionType: ExceptionType;
  label: string;
  description: string;
  occurredAt: string;
  /** 未解決なら null。 */
  resolvedAt: string | null;
  resolutionNotes: string | null;
  urgent: boolean;
};

/** 起票された例外。 */
export type TrackingException = {
  id: number;
  exceptionType: ExceptionType;
  /** 表示名。**画面が対訳表を持たない**（[ADR-023] 決定 1 と同じ形）。 */
  label: string;
  description: string;
  occurredAt: string;
  /** 紛失だけが真（[ADR-024] 決定 3）。 */
  urgent: boolean;
};

/** 起票できる種別と、その表示名。**画面が対訳表を持たない**（[ADR-023] 決定 1 と同じ形）。 */
/**
 * 手で更新できる状態と、その表示名。
 *
 * **進める先だけを返す。** 戻る向きの選択肢を出しておいて 409 で断るのは、
 * 押せるのに断られる操作を出すことである（[ADR-024] 決定 1）。
 */
export type TrackingStatusChoice = {
  status: TrackingStatus;
  label: string;
};

export type ExceptionTypeChoice = {
  exceptionType: ExceptionType;
  label: string;
  urgent: boolean;
};

export type ManualUpdateRequest = {
  trackingNumber: string;
  status: TrackingStatus;
  locationUnLocode: string;
  occurredAt: string;
};

export type RaiseExceptionRequest = {
  trackingNumber: string;
  exceptionType: ExceptionType;
  description: string;
};

export type ResolveExceptionRequest = {
  trackingNumber: string;
  exceptionId: number;
  resolutionNotes: string;
  /** 新しい到着予定日（US19-4）。遅延以外では空のこともある。 */
  newEstimatedArrival: string | null;
};

/** 未解決例外の件数（横断規約）。**件数から一覧へ辿れること**。 */
export type OpenExceptionSummary = {
  count: number;
  urgentCount: number;
};
