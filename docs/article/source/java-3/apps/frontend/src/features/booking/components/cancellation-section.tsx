import { useState } from "react";
import {
  useCancellation,
  useCancellationHistory,
  useRequestCancellation,
} from "../../cancellation/queries";
import { ApiError } from "../../../lib/api-client";
import type { Booking } from "../types";
import { can } from "../types";

/**
 * 予約のキャンセル申請（US30-1・US30-2・US30-3）。
 *
 * <p><strong>営業担当者の手番。</strong>荷主から「止めてほしい」と言われるのは営業である。
 *
 * <p><strong>輸送開始前と輸送中で結果が違う。</strong>輸送開始前はその場で確定し、
 * 輸送中は追跡管理者の承認を待つ——貨物が船の上にあり、<strong>どこで降ろすかを
 * 決めないとキャンセルできない</strong>。
 *
 * <p><strong>出し分けはサーバが返す「行える操作」に従う</strong>（[ADR-021] と同じ形）。
 * 画面が状態名を見比べると、遷移の規則が集約・画面・モックの 3 か所に分かれる。
 */
export function CancellationSection({
  booking,
  isSales,
}: Readonly<{ booking: Booking; isSales: boolean }>) {
  const { data: cancellation } = useCancellation(booking.bookingId);
  const { data: history = [] } = useCancellationHistory(booking.bookingId);
  const request = useRequestCancellation(booking.bookingId);

  const [requesting, setRequesting] = useState(false);
  const [reason, setReason] = useState("");
  const [outcome, setOutcome] = useState<string | null>(null);

  const canRequest = isSales && can(booking, "REQUEST_CANCELLATION");
  const failure = request.error instanceof ApiError ? request.error.message : null;

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    request.mutate(
      { reason },
      {
        onSuccess: (result) => {
          setRequesting(false);
          setReason("");
          setOutcome(
            result.awaitingApproval
              ? "キャンセルを申請しました。追跡管理者の承認をお待ちください。"
              : "キャンセルが確定しました。",
          );
        },
      },
    );
  }

  if (!canRequest && cancellation == null) {
    return null;
  }

  // 最新の 1 件は上の欄に出る。**履歴はそれより前の分**を並べる
  const earlier = history.slice(1);

  return (
    <section className="space-y-4 rounded border border-gray-200 p-4">
      <h2 className="text-lg font-semibold text-gray-900">キャンセル</h2>

      {outcome !== null && (
        <output
          className="rounded border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-900 block"
        >
          {outcome}
        </output>
      )}

      {failure !== null && (
        <p
          role="alert"
          className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900"
        >
          {failure}
        </p>
      )}

      {cancellation != null && (
        <dl className="grid gap-2 text-sm md:grid-cols-2">
          <div>
            <dt className="text-gray-600">申請の状態</dt>
            <dd className="font-medium">{cancellation.statusLabel}</dd>
          </div>
          <div>
            <dt className="text-gray-600">理由</dt>
            <dd>{cancellation.reason}</dd>
          </div>
          <div>
            <dt className="text-gray-600">申請者・申請日時</dt>
            <dd>
              {cancellation.requestedBy}・{cancellation.requestedAt}
            </dd>
          </div>
          {cancellation.dischargeLocationName !== null && (
            <div>
              <dt className="text-gray-600">陸揚げ地</dt>
              {/* **荷降しの作業指示は自動では作られない**（[ADR-025] 決定 5）。
                  荷役の担当者はここを見る */}
              <dd className="font-medium">
                {cancellation.dischargeLocationName}
                <span className="ml-2 text-xs text-gray-600">
                  （荷降しの手配は担当者への連絡で行います）
                </span>
              </dd>
            </div>
          )}
          {cancellation.decisionReason !== null && (
            <div>
              <dt className="text-gray-600">決定の理由</dt>
              <dd>{cancellation.decisionReason}</dd>
            </div>
          )}
          {cancellation.decidedBy !== null && (
            <div>
              <dt className="text-gray-600">決定者・決定日時</dt>
              <dd>
                {cancellation.decidedBy}・{cancellation.decidedAt}
              </dd>
            </div>
          )}
        </dl>
      )}

      {/* **これまでの申請も残す**（US30-10）。却下されて再申請した予約では、
          前回の却下理由が次の判断の材料になる——「なぜ一度断られたか」は、
          次に荷主と話す営業がいちばん必要とする情報である */}
      {earlier.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-gray-900">これまでの申請</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-left text-gray-600">
                <th className="py-2">申請日時</th>
                <th className="py-2">申請者</th>
                <th className="py-2">理由</th>
                <th className="py-2">結果</th>
                <th className="py-2">決定者・決定日時</th>
                <th className="py-2">決定の理由</th>
              </tr>
            </thead>
            <tbody>
              {earlier.map((past) => (
                <tr key={past.cancellationId} className="border-b border-gray-100">
                  <td className="py-2">{past.requestedAt}</td>
                  <td className="py-2">{past.requestedBy}</td>
                  <td className="py-2">{past.reason}</td>
                  <td className="py-2">{past.statusLabel}</td>
                  <td className="py-2">
                    {past.decidedBy === null
                      ? "-"
                      : `${past.decidedBy}・${past.decidedAt ?? ""}`}
                  </td>
                  <td className="py-2">{past.decisionReason ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {canRequest &&
        (requesting ? (
          <form onSubmit={submit} className="space-y-4">
            <div>
              <label
                htmlFor="cancellationReason"
                className="block text-sm font-medium text-gray-700"
              >
                キャンセルの理由
              </label>
              <input
                id="cancellationReason"
                required
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
              />
            </div>

            <p className="text-sm text-gray-600">
              {booking.bookingStatus === "IN_TRANSIT" ? (
                <>
                  {'この貨物は'}<strong>輸送中</strong>{'です。申請は'}
                  <strong>追跡管理者の承認</strong>を経て確定します——
                  どこで荷降しするかを決める必要があるためです。
                </>
              ) : (
                <>
                  {'この貨物はまだ動いていないため、申請すると'}
                  <strong>その場でキャンセルが確定します</strong>。
                </>
              )}
            </p>

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={request.isPending}
                className="rounded bg-red-600 px-4 py-2 text-white disabled:opacity-50"
              >
                申請する
              </button>
              <button
                type="button"
                onClick={() => setRequesting(false)}
                className="rounded border border-gray-300 px-4 py-2"
              >
                やめる
              </button>
            </div>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => {
              setOutcome(null);
              setRequesting(true);
            }}
            className="rounded border border-red-300 px-4 py-2 text-red-700"
          >
            キャンセルを申請する
          </button>
        ))}
    </section>
  );
}
