import { useState } from "react";
import { Link } from "react-router-dom";
import {
  useApproveCancellation,
  usePendingCancellations,
  useRejectCancellation,
} from "../features/cancellation/queries";
import type { PendingCancellation } from "../features/cancellation/types";
import { ApiError } from "../lib/api-client";

/**
 * 輸送中のキャンセル承認（US30-4・US30-5・US30-7）。
 *
 * **追跡管理者が使う。** 貨物が船の上にあるため、**どこで降ろすかを決めないと
 * キャンセルできない**。
 *
 * **陸揚げ地は候補から選ぶ**（[ADR-025] 決定 4）。全港から選ばせると、船が寄らない
 * 港を指定でき、荷降しできない約束を荷主にすることになる。候補はサーバが作る
 * ——「現在地の港」と「次の寄港地」であり、画面が旅程から組み立てない。
 */
function DecisionForm({
  cancellation,
  onDone,
}: Readonly<{ cancellation: PendingCancellation; onDone: (message: string) => void }>) {
  const approve = useApproveCancellation(cancellation.bookingId);
  const reject = useRejectCancellation(cancellation.bookingId);

  const [dischargeLocationUnLocode, setDischargeLocationUnLocode] = useState("");
  const [decisionReason, setDecisionReason] = useState("");

  // **承認と却下の失敗を 1 か所で受ける。** 入れ子の三項にすると、
  // 「どちらの失敗を出しているか」が読み取りにくくなる
  const failed = [approve.error, reject.error].find(
    (error) => error instanceof ApiError,
  );
  const failure = failed instanceof ApiError ? failed.message : null;

  function submitApprove(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    approve.mutate(
      { dischargeLocationUnLocode, decisionReason },
      { onSuccess: () => onDone("キャンセルを承認しました。") },
    );
  }

  return (
    <section className="space-y-4 rounded border border-gray-200 p-4">
      <h2 className="text-lg font-semibold text-gray-900">
        承認操作（{cancellation.bookingId}）
      </h2>

      {failure !== null && (
        <p
          role="alert"
          className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900"
        >
          {failure}
        </p>
      )}

      <form onSubmit={submitApprove} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label
              htmlFor="dischargeLocation"
              className="block text-sm font-medium text-gray-700"
            >
              陸揚げ地
            </label>
            {/* 候補はサーバが作る。なぜ候補なのかを添える——港の名前だけを並べると、
                追跡管理者はどれを選べばよいか決められない */}
            <select
              id="dischargeLocation"
              required
              value={dischargeLocationUnLocode}
              onChange={(event) => setDischargeLocationUnLocode(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            >
              <option value="">選んでください</option>
              {cancellation.dischargeCandidates.map((candidate) => (
                <option key={candidate.unLocode} value={candidate.unLocode}>
                  {candidate.name}（{candidate.reason}）
                </option>
              ))}
            </select>
          </div>
          <div>
            <label
              htmlFor="decisionReason"
              className="block text-sm font-medium text-gray-700"
            >
              決定の理由（却下のときは必須）
            </label>
            <input
              id="decisionReason"
              value={decisionReason}
              onChange={(event) => setDecisionReason(event.target.value)}
              className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
            />
            {/* **断られてから必須と知る形にしない。** 判断が重く毎日は使わない画面であり、
                却下の理由は申請した営業担当者が読む——次にどうするかが分かるように書いて
                もらう必要がある */}
            <p className="mt-1 text-sm text-gray-600">
              却下するときは理由を入れてください。申請した営業担当者が読みます。
            </p>
          </div>
        </div>

        <p className="text-sm text-gray-600">
          承認すると<strong>キャンセルが確定します</strong>。指定した陸揚げ地は予約詳細に
          {'残り、荷役の担当者が確認します。'}
          <strong>荷降しの作業指示は自動では作られません</strong>——担当者へ連絡してください。
        </p>

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={approve.isPending}
            className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
          >
            承認する
          </button>
          <button
            type="button"
            disabled={reject.isPending}
            onClick={() =>
              reject.mutate(
                { decisionReason },
                {
                  onSuccess: () =>
                    onDone("キャンセルを却下しました。予約は輸送中のままです。"),
                },
              )
            }
            className="rounded border border-gray-300 px-4 py-2 disabled:opacity-50"
          >
            却下する
          </button>
        </div>
      </form>
    </section>
  );
}

export function CancellationsPage() {
  const { data: pending = [], isLoading } = usePendingCancellations();
  const [openId, setOpenId] = useState<number | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const open = pending.find(
    (cancellation) => cancellation.cancellationId === openId,
  );

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">
        キャンセル承認（承認待ち一覧）
      </h1>

      {done !== null && (
        <div className="space-y-2">
          <output
            className="rounded border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-900 block"
          >
            {done}
          </output>
          {/*
            US30-6・US30-7 は**代替**である（通知の仕組みがまだ無い）。**送っていない
            ことを画面が言う**（IT8 と同じ形）。書かないと、追跡管理者は「承認したから
            荷主に届いた」と受け取って連絡をせず、荷主は自分の申し入れがどうなったかを
            知らないままになる。
          */}
          <p className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            <strong>荷主と申請者へのご連絡は自動では行われません。</strong>
            {/* 改行を空白と読ませない（日本語は語間を空けない） */}
            {'決定の内容は、担当者からお伝えください。承認した場合は'}
            <strong>荷役の担当者にも陸揚げ地をご連絡ください</strong>
            {'（荷降しの作業指示は自動では作られません）。'}
          </p>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-600">読み込んでいます…</p>}

      {!isLoading && pending.length === 0 ? (
        <p className="text-sm text-gray-600">
          承認待ちのキャンセル申請はありません。
        </p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-gray-600">
              <th className="py-2">予約 ID</th>
              <th className="py-2">申請日時</th>
              <th className="py-2">申請者</th>
              <th className="py-2">理由</th>
              <th className="py-2">予約状態</th>
              <th className="py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            {pending.map((cancellation) => (
              <tr
                key={cancellation.cancellationId}
                className="border-b border-gray-100"
              >
                <td className="py-2">
                  {/* **一覧を行き止まりにしない。** 承認の判断には荷主・貨物種別・
                      旅程を見たくなる——別画面で探し直させない */}
                  <Link
                    to={`/booking/${encodeURIComponent(cancellation.bookingId)}`}
                    className="text-blue-600 hover:underline"
                  >
                    {cancellation.bookingId}
                  </Link>
                </td>
                <td className="py-2">{cancellation.requestedAt}</td>
                <td className="py-2">{cancellation.requestedBy}</td>
                <td className="py-2">{cancellation.reason}</td>
                {/* 申請時点の予約状態は**キャンセル料の根拠**になる（US23・IT11） */}
                <td className="py-2">{cancellation.bookingStatusAtRequestLabel}</td>
                <td className="py-2">
                  <button
                    type="button"
                    onClick={() => {
                      setDone(null);
                      setOpenId(cancellation.cancellationId);
                    }}
                    className="text-blue-600 underline"
                  >
                    開く
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {open !== undefined && (
        <DecisionForm
          cancellation={open}
          onDone={(message) => {
            setDone(message);
            setOpenId(null);
          }}
        />
      )}
    </div>
  );
}
