import { Link } from "react-router-dom";
import { useAwaitingDischarge } from "../features/cancellation/queries";

/**
 * 陸揚げ待ちの一覧（IT10 返済枠 0.3）。
 *
 * **荷役の担当者には、陸揚げ地が決まったことを知る入口が無かった。**
 * キャンセルが承認されても作業指示は自動で作られず（[ADR-025] 決定 5）、
 * 承認した追跡管理者からの連絡が唯一の担保だった——**連絡を忘れると、貨物は
 * 指定した港を通り過ぎる**。
 *
 * **古い順に並べる。** 承認から時間が経つほど、船は港に近づく。
 */
export function AwaitingDischargePage() {
  const { data: awaiting = [], isLoading } = useAwaitingDischarge();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">陸揚げ待ち</h1>
        <Link to="/dashboard" className="text-blue-600 hover:underline">
          ダッシュボードに戻る
        </Link>
      </div>

      <p className="text-sm text-gray-700">
        {/* 改行を空白と読ませない（日本語は語間を空けない） */}
        {"キャンセルが承認され、"}
        <strong>陸揚げ地が決まった貨物</strong>
        {"です。作業指示は自動では作られません——この一覧を見て手配してください。"}
      </p>

      {isLoading && <p className="text-sm text-gray-600">読み込んでいます…</p>}

      {!isLoading && awaiting.length === 0 ? (
        <p className="text-sm text-gray-600">陸揚げ待ちの貨物はありません。</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-gray-600">
              <th className="py-2">予約 ID</th>
              <th className="py-2">陸揚げ地</th>
              <th className="py-2">承認者・承認日時</th>
              <th className="py-2">キャンセルの理由</th>
            </tr>
          </thead>
          <tbody>
            {awaiting.map((cancellation) => (
              <tr
                key={cancellation.cancellationId}
                className="border-b border-gray-100"
              >
                <td className="py-2">
                  {/* 一覧を行き止まりにしない。貨物の中身は予約詳細で見る */}
                  <Link
                    to={`/booking/${encodeURIComponent(cancellation.bookingId)}`}
                    className="text-blue-600 hover:underline"
                  >
                    {cancellation.bookingId}
                  </Link>
                </td>
                <td className="py-2 font-medium">
                  {cancellation.dischargeLocationName ??
                    cancellation.dischargeLocationUnLocode}
                </td>
                <td className="py-2">
                  {cancellation.decidedBy}・{cancellation.decidedAt}
                </td>
                <td className="py-2">{cancellation.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
