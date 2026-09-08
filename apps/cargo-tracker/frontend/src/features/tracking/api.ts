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
  /**
   * いま手で動かせる先。
   *
   * <p><b>画面が遷移表を持たない。</b> 持つと判定が 2 つになり、集約が断る先を
   * 画面が出してしまう（押してから断られる）。サーバが集約と同じ述語で決める。</p>
   */
  readonly nextStatuses: readonly string[];
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
