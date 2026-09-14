import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ALERT,
  BUTTON_DANGER,
  CARD,
  FIELD,
  LABEL,
  NOTICE,
  SECTION_TITLE,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import { cancellableImmediately, canTransitionTo } from './transitions';
import { fetchCancellationsOfBooking, requestCancellation } from './cancellationApi';

/**
 * S22 のキャンセル欄（UC22 / US30 §受入基準 1・2・3・10）。
 *
 * <p><b>入口は 1 つ。</b> 輸送開始前は即座にキャンセル、輸送中は申請になる
 * ——どちらになるかは集約が状態から決める。<b>画面はボタンの文言だけを変える</b>
 * （判断を書き直さない）。</p>
 *
 * <p><b>履歴は誰でも読める。</b> 申請したのは営業、判断するのは追跡管理者なので、
 * 「いま何が起きているか」を両方が読めなければ話が噛み合わない。</p>
 */
export function BookingCancellationPanel({ bookingId, bookingStatus, canRequest }: {
  bookingId: string;
  bookingStatus: string;
  /** 申請できる人か（営業）。**読むのは全員**。 */
  canRequest: boolean;
}) {
  const client = useQueryClient();
  const [reason, setReason] = useState('');

  const history = useQuery({
    queryKey: ['cancellations', bookingId],
    queryFn: () => fetchCancellationsOfBooking(bookingId),
  });

  const request = useMutation({
    mutationFn: () => requestCancellation(bookingId, reason),
    onSuccess: async () => {
      setReason('');
      await Promise.all([
        client.invalidateQueries({ queryKey: ['cancellations', bookingId] }),
        client.invalidateQueries({ queryKey: ['booking', bookingId] }),
      ]);
    },
  });

  // **1 つの読み口の不調で画面全体を落とさない。** 履歴が読めなくても、予約詳細の
  // 他の欄は読めなければならない——落とすと、営業は状態も旅程も確かめられなくなる。
  const items = history.data?.state === 'ready' ? history.data.value.items ?? [] : [];
  const pending = items.some((item) => item.decision === null);
  // **キャンセルできるかは遷移表が決める。** 画面で状態を数え直さない。
  const cancellable = canTransitionTo(bookingStatus, 'CANCELLED');
  // **輸送中だけ承認が要る**（不変条件 9）。文言はここで変えるが、**判断はしない**
  // ——どちらの経路になるかは正典（`BookingStatus#cancellableImmediately`）が決め、
  // `transitions.canon.test.ts` が写しのずれを赤にする。
  const needsApproval = cancellable && !cancellableImmediately(bookingStatus);

  if (!cancellable && items.length === 0) {
    return null;
  }

  return (
    <section className={`${CARD} mt-4`}>
      <h2 className={SECTION_TITLE}>キャンセル</h2>

      {cancellable && canRequest && !pending && (
        <form
          className="mt-3 flex flex-wrap items-end gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            request.mutate();
          }}
        >
          <div className="grow">
            <label className={LABEL} htmlFor="cancellation-reason">理由</label>
            <input
              id="cancellation-reason"
              className={FIELD}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="荷主の発注取消"
            />
          </div>
          {/* **輸送中は申請。** 荷物が船の上にある以上、どこで降ろすかを決めな
              ければ止めたことにならない——追跡管理者が承認する。 */}
          <button
            className={BUTTON_DANGER}
            type="submit"
            disabled={request.isPending || reason.trim() === ''}
          >
            {needsApproval ? 'キャンセル（要承認）' : 'キャンセルする'}
          </button>
          {request.isPending && <output className={NOTICE}>送信中…</output>}
        </form>
      )}

      {needsApproval && (
        <p className="mt-2 text-sm text-gray-600">
          輸送中の予約は<b>追跡管理者の承認</b>が要ります。承認されると、指定した港で
          荷降しが手配されます。
        </p>
      )}

      {pending && (
        <output className={`${NOTICE} mt-3 block`}>
          承認待ちの申請があります。判断されるまで、新しい申請は出せません。
        </output>
      )}

      {request.isError && (
        <p role="alert" className={`${ALERT} mt-3`}>
          {request.error instanceof ApiError
            ? request.error.message
            : 'キャンセルを申し出られませんでした'}
        </p>
      )}

      {items.length > 0 && (
        <ul className="mt-3 space-y-2">
          {items.map((item) => (
            <li
              key={item.requestId}
              data-testid={`cancellation-history-${item.requestId}`}
              className="border-t border-gray-100 pt-2 text-sm first:border-0 first:pt-0"
            >
              {/* **誰が・いつ・なぜが揃って初めて履歴になる。** */}
              <p className="text-gray-800">
                {formatBusinessDateTime(item.requestedAt)} 申請（{item.requestedBy}）:
                {' '}{item.reason}
              </p>
              <p className="text-gray-600">
                {item.decision === null
                  ? '承認待ち'
                  : `${formatBusinessDateTime(item.decidedAt ?? item.requestedAt)} `
                    + `${item.decisionLabel}（${item.decidedBy}）`
                    + `${item.dischargeUnLocode ? `・陸揚げ地 ${item.dischargeUnLocode}` : ''}`
                    + `${item.decisionReason ? `・${item.decisionReason}` : ''}`}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
