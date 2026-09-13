import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/**
 * キャンセル申請 1 件（S22 の履歴・S23 の承認待ち）。
 *
 * `decision` は **承認済 / 却下済 / 未判断（null）**。「承認待ちか」を別の項目に
 * 持たない——同じ事実を 2 か所が持つと、片方だけが更新された行が生まれる。
 */
export interface CancellationRequestView {
  readonly requestId: string;
  readonly bookingId: string;
  readonly bookingNumber: string | null;
  readonly productName: string | null;
  readonly reason: string;
  readonly requestedBy: string;
  readonly requestedAt: string;
  readonly decision: string | null;
  readonly decisionLabel: string;
  readonly dischargeUnLocode: string | null;
  readonly decisionReason: string | null;
  readonly decidedBy: string | null;
  readonly decidedAt: string | null;
}

/**
 * 陸揚げ地の選択肢（S23）。
 *
 * **画面が組み立てない。** 集約が断る条件と同じ関数から作るので、出ているのに
 * 押すと断られる港が生まれない。先頭が現在地。
 */
export interface DischargeCandidatesView {
  readonly currentUnLocode: string | null;
  readonly unLocodes: readonly string[];
}

/**
 * キャンセルを申し出る（US30 §受入基準 1・2・3）。
 *
 * **入口は 1 つ。** 輸送開始前は即座にキャンセルになり、輸送中は申請になる
 * ——どちらになるかは集約が状態から決める。画面が出し分けると、同じ判断が
 * 2 か所に住むことになる。
 */
export function requestCancellation(bookingId: string, reason: string): Promise<void> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/cancellation`,
    { reason },
  );
}

/** 承認する（US30 §受入基準 5・6）。**追跡管理者の操作**。 */
export function approveCancellation(
  bookingId: string,
  dischargeUnLocode: string,
  reason: string,
): Promise<void> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/cancellation/approval`,
    { dischargeUnLocode, reason },
  );
}

/** 却下する（US30 §受入基準 7）。**予約の状態は動かない**。 */
export function rejectCancellation(bookingId: string, reason: string): Promise<void> {
  return commandClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/cancellation/rejection`,
    { reason },
  );
}

/** 承認待ちの申請（S23 / US30 §受入基準 4）。 */
export function fetchPendingCancellations(): Promise<
  Pending<{ items: CancellationRequestView[] }>
> {
  return queryClient('/booking/bookings/cancellations');
}

/** その予約のキャンセル履歴（S22 / US30 §受入基準 10）。 */
export function fetchCancellationsOfBooking(
  bookingId: string,
): Promise<Pending<{ items: CancellationRequestView[] }>> {
  return queryClient(`/booking/bookings/${encodeURIComponent(bookingId)}/cancellation`);
}

/** 陸揚げ地の選択肢（S23 / US30 §受入基準 5）。 */
export function fetchDischargeCandidates(
  bookingId: string,
): Promise<Pending<DischargeCandidatesView>> {
  return queryClient(
    `/booking/bookings/${encodeURIComponent(bookingId)}/cancellation/discharge-candidates`,
  );
}
