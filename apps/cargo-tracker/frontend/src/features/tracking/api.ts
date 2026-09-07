import { queryClient } from '@/shared/api/client';
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
