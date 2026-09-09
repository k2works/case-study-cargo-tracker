import { businessLocalToInstant } from '@/shared/api/businessDate';
import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** 公開照会の履歴 1 行（S44 / US18）。 */
export interface PublicTrackingEventView {
  readonly occurredAt: string;
  /** 状態の呼び名。**列挙名は来ない**（要素表が正典）。 */
  readonly statusLabel: string;
  readonly location: string | null;
}

/**
 * 公開照会で見せる中身（S44 / US18）。
 *
 * <p><b>荷主名・予約 ID・例外の詳細・金額は入らない。</b> サーバが渡さないので、
 * 画面が間違えても漏れない（ui_design.md「公開画面には出さない」）。</p>
 */
export interface PublicTrackingView {
  readonly trackingNumber: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly statusLabel: string;
  readonly currentUnLocode: string | null;
  readonly departedAt: string | null;
  /** 到着予定。予定の旅程の最終区間の荷降し（サーバが 1 か所で決める）。 */
  readonly estimatedArrival: string | null;
  readonly history: readonly PublicTrackingEventView[];
}

/**
 * 追跡番号の形（[ADR-0011]）。`TRK-` + 大文字英数字 10 桁。
 *
 * <p><b>大文字小文字とハイフンの有無を吸収する。</b> 荷受人は案内のメールから
 * 手で打ち直す。形が合わないだけで「見つかりません」と返すと、番号が正しいのに
 * 諦めてしまう。</p>
 */
export function normalizeTrackingNumber(input: string): string {
  const bare = input.trim().toUpperCase().replace(/^TRK-?/, '');
  return `TRK-${bare}`;
}

/** 形式が正しいか（照会の前に画面が知らせる）。 */
export function isWellFormedTrackingNumber(input: string): boolean {
  return /^TRK-[0-9A-Z]{10}$/.test(normalizeTrackingNumber(input));
}

/** 公開照会（認証不要）。見つからなければ ApiError(404)。 */
export function fetchPublicTracking(trackingNumber: string): Promise<Pending<PublicTrackingView>> {
  return queryClient(
    `/tracking/public/${encodeURIComponent(normalizeTrackingNumber(trackingNumber))}`,
  );
}

/** 追跡一覧の 1 行（S40）。 */
export interface TrackingListItemView {
  readonly trackingNumber: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly statusLabel: string;
  readonly currentUnLocode: string | null;
  readonly estimatedArrival: string | null;
  readonly lastStatusChangedAt: string;
}

/** 追跡詳細の履歴 1 行（S41）。公開照会と違い**誰が動かしたか**も出る。 */
export interface TrackingEventView {
  readonly occurredAt: string;
  readonly eventType: string;
  readonly previousStatusLabel: string | null;
  readonly statusLabel: string;
  readonly location: string | null;
  readonly recordedBy: string | null;
}

/** 追跡詳細（S41）。 */
/** 追跡詳細に出す例外 1 件（S41）。**解決したものも出す**（不変条件 6）。 */
export interface TrackingExceptionView {
  readonly exceptionId: string;
  readonly exceptionType: ExceptionType;
  readonly exceptionTypeLabel: string;
  readonly responseStatus: string;
  readonly responseStatusLabel: string;
  readonly urgent: boolean;
  readonly unLocode: string | null;
  readonly description: string;
  readonly resolution: string | null;
  /** 対応で示した新しい到着予定日（US19 §4）。一覧の並びに効く。 */
  readonly newEstimatedArrival: string | null;
  readonly responsePlan: string | null;
  readonly occurredAt: string;
  readonly resolvedAt: string | null;
  /**
   * 荷主へ知らせた記録（US19 §3）。
   *
   * <p><b>送信基盤はスコープ外</b>なので、これが読めることでしか受入基準を
   * 満たせない。記録だけして読めなければ、記録していないのと同じ。</p>
   */
  readonly notifications: readonly ExceptionNotificationView[];
  /**
   * 決着したか（サーバが `ResponseStatus#settled` で決める）。
   *
   * <p><b>画面で `responseStatus !== 'RESOLVED'` と書き直さない。</b> 状態が
   * 増えたときに片方だけが直る（IT10 レビュー N3。三重定義だった）。</p>
   */
  readonly settled: boolean;
}

/** 荷主へ知らせた記録 1 件（S41）。 */
export interface ExceptionNotificationView {
  readonly means: string;
  readonly summary: string;
  readonly notifiedBy: string | null;
  readonly notifiedAt: string;
}

export interface TrackingView {
  readonly trackingNumber: string;
  readonly bookingId: string;
  readonly originUnLocode: string;
  readonly destinationUnLocode: string;
  readonly cargoType: string;
  readonly status: string;
  readonly statusLabel: string;
  readonly currentUnLocode: string | null;
  readonly estimatedArrival: string | null;
  readonly lastStatusChangedAt: string;
  readonly history: readonly TrackingEventView[];
  /** その追跡の例外（US19）。**解決したものも出す**（不変条件 6）。 */
  readonly exceptions: readonly TrackingExceptionView[];
  /**
   * いま手で動かせる先。
   *
   * <p><b>画面が遷移表を持たない。</b> 持つと判定が 2 つになり、集約が断る先を
   * 画面が出してしまう（押してから断られる）。サーバが集約と同じ述語で決める。</p>
   */
  readonly nextStatuses: readonly string[];
  /**
   * 誤配として扱っているか（US28 §3）。
   *
   * <p><b>状態とは別に持つ。</b> 例外の対応中は状態が「例外発生」へ退避するが、
   * 誤配であることは変わらない。状態から導くと、バナーが例外の起票と同時に
   * 消えてしまう。</p>
   */
  readonly misrouted: boolean;
}

/** 追跡一覧（S40）。荷主には自社のぶんだけが返る（サーバがヘッダで絞る）。 */
export function fetchTrackings(
  includeDelivered: boolean,
): Promise<Pending<{ items: TrackingListItemView[]; total: number }>> {
  return queryClient(`/tracking/trackings?includeDelivered=${includeDelivered}`);
}

/** 追跡詳細（S41）。 */
export function fetchTracking(trackingNumber: string): Promise<Pending<TrackingView>> {
  return queryClient(`/tracking/trackings/${encodeURIComponent(trackingNumber)}`);
}

/** 状態を手で更新する（S41 / US17 §2）。追跡管理者だけ。 */
export function updateTransportStatus(
  trackingNumber: string,
  input: {
    readonly newStatus: string;
    readonly location: string;
    /** 起きた日時（業務時刻の `YYYY-MM-DDTHH:mm`）。空ならサーバの業務時計で「いま」。 */
    readonly occurredAt: string;
  },
): Promise<void> {
  // **日時を空で送ったときはサーバの業務時計で決める。** ブラウザの時計は利用者の
  // 設定に左右され、履歴の並びが端末ごとに変わる。
  return commandClient(`/tracking/trackings/${encodeURIComponent(trackingNumber)}/status`, {
    newStatus: input.newStatus,
    location: input.location || null,
    occurredAt: input.occurredAt === '' ? null : businessLocalToInstant(input.occurredAt),
  });
}

/** 直近で状態が変わった追跡の件数（S02 荷主 / US17 §4 の代わり）。 */
export interface RecentlyChangedView {
  readonly count: number;
  readonly withinHours: number;
}

/**
 * 直近で状態が変わった件数（S02 荷主）。
 *
 * <p>荷主には「変わったこと」を知る手段がなく、一覧を毎回見比べるしかなかった。
 * 送信基盤なしで満たすための受け皿。</p>
 */
export function fetchRecentlyChanged(): Promise<Pending<RecentlyChangedView>> {
  return queryClient('/tracking/trackings/recently-changed');
}

/** 例外の種別（domain-model.md の要素表・不変条件 7）。 */
export type ExceptionType = 'DELAY' | 'DAMAGE' | 'LOSS' | 'MISROUTE' | 'CUSTOMS_HOLD';

/**
 * 呼び名。<b>正典は要素表</b>で、`exceptionTypeLabels.test.ts` が読んで突き合わせる。
 *
 * <p>サーバも一覧に呼び名を載せるが、S43 の選択肢はサーバへ問い合わせずに出す
 * ——起票の画面を開いた時点で決まっており、1 往復増やす理由がない。</p>
 */
export const EXCEPTION_TYPE_LABELS: Record<ExceptionType, string> = {
  DELAY: '遅延',
  DAMAGE: '破損',
  LOSS: '紛失',
  MISROUTE: '誤配',
  CUSTOMS_HOLD: '税関保留',
};

/**
 * 手で起票してよい種別（S43）。
 *
 * <p><b>誤配と税関保留は出さない。</b> どちらもシステムが自動で起票する
 * （US28・UC21）——手で選べるようにすると、起きていない誤配を記録できてしまう
 * （`TransportStatus#isSetByHand` と同じ考え方）。</p>
 */
export const REPORTABLE_EXCEPTION_TYPES: readonly ExceptionType[] =
  ['DELAY', 'DAMAGE', 'LOSS'];

/** 例外一覧の 1 行（S42）。 */
export interface ExceptionView {
  readonly exceptionId: string;
  readonly trackingNumber: string;
  readonly exceptionType: ExceptionType;
  readonly exceptionTypeLabel: string;
  readonly responseStatus: string;
  readonly responseStatusLabel: string;
  readonly urgent: boolean;
  readonly unLocode: string | null;
  readonly description: string;
  readonly occurredAt: string;
  readonly estimatedArrival: string | null;
  readonly transportStatus: string;
  readonly transportStatusLabel: string;
  /** 予約番号。電話は「A 社の予約の件で」から始まる（IT10 レビュー N9）。 */
  readonly bookingId: string | null;
  /** 上位者へ知らせた時刻（US20 §3）。null なら未 escalation。 */
  readonly escalatedAt: string | null;
}

/**
 * 未解決の例外（S42 / US19 §5）。
 *
 * <p><b>並びはサーバが決める。</b> 緊急が先、以降は到着期限までの残日数が
 * 少ない順（不変条件 7）。画面で並べ直すと判定が 2 か所になる。</p>
 */
export function fetchOpenExceptions(
  options: { readonly includeResolved?: boolean } = {},
): Promise<Pending<{ items: ExceptionView[] }>> {
  // **既定では解決済を外す。** 決着したものが混ざると、一覧全体が
  // 「まだ手を入れる場所」に見えなくなる。切替は US28 §8 が要る——
  // 誤配の事実は解決後も料金調整の根拠として参照される。
  const query = options.includeResolved ? '?includeResolved=true' : '';
  return queryClient(`/tracking/trackings/exceptions${query}`);
}

/** 例外を起票する（S43 / US19 §1）。 */
export function registerException(
  trackingNumber: string,
  input: {
    readonly exceptionType: ExceptionType;
    readonly unLocode: string;
    readonly description: string;
    /** 起きた日時（業務時刻の `YYYY-MM-DDTHH:mm`）。空ならサーバの業務時計で「いま」。 */
    readonly occurredAt: string;
  },
): Promise<void> {
  return commandClient(`/tracking/trackings/${encodeURIComponent(trackingNumber)}/exceptions`, {
    exceptionType: input.exceptionType,
    unLocode: input.unLocode || null,
    description: input.description,
    occurredAt: input.occurredAt === '' ? null : businessLocalToInstant(input.occurredAt),
  });
}

/** 例外の経路。階層が深いので 1 か所で組む。 */
function exceptionPath(trackingNumber: string, exceptionId: string): string {
  return `/tracking/trackings/${encodeURIComponent(trackingNumber)}`
    + `/exceptions/${encodeURIComponent(exceptionId)}`;
}

/** 対応を始める（S41 / US19 §4）。 */
export function startExceptionResponse(
  trackingNumber: string,
  exceptionId: string,
  input: { readonly newEstimatedArrival: string; readonly plan: string },
): Promise<void> {
  return commandClient(`${exceptionPath(trackingNumber, exceptionId)}/response`, {
    newEstimatedArrival: input.newEstimatedArrival || null,
    plan: input.plan,
  });
}

/** 解決する（S41 / US19 §4）。 */
export function resolveException(
  trackingNumber: string,
  exceptionId: string,
  resolution: string,
): Promise<void> {
  return commandClient(`${exceptionPath(trackingNumber, exceptionId)}/resolution`, {
    resolution,
  });
}

/**
 * 荷主へ知らせた事実を記録する（S41 / US19 §3）。
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。通知は電話・メールで行い、
 * ここに残すのは「いつ・どうやって・何を伝えたか」だけ。</p>
 */
export function notifyShipperOfException(
  trackingNumber: string,
  exceptionId: string,
  input: { readonly means: string; readonly summary: string },
): Promise<void> {
  return commandClient(`${exceptionPath(trackingNumber, exceptionId)}/notifications`, input);
}
