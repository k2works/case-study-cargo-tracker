import { Link } from "react-router-dom";
import { useOpenExceptionList } from "../features/tracking/queries";

/**
 * 未解決の例外がある貨物の一覧（横断規約）。
 *
 * **件数の遷移先である。** 件数を出すだけでは仕事は進まない——「3 件ある」と分かっても、
 * どの貨物かが分からなければ次にすることが無い（[IT7 の学び](../../docs/development/retrospective-7.md)）。
 *
 * **一覧から個別の管理画面へ行ける。** 一覧が行き止まりだと、番号を書き写して
 * 打ち直すことになる。
 */
export function TrackingExceptionsPage() {
  const { data: trackings = [], isLoading } = useOpenExceptionList();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">未解決の例外</h1>
        <Link to="/tracking/manage" className="text-blue-600 hover:underline">
          貨物状態の管理に戻る
        </Link>
      </div>

      {isLoading && <p className="text-sm text-gray-600">読み込んでいます…</p>}

      {!isLoading && trackings.length === 0 ? (
        <p className="text-sm text-gray-600">未解決の例外はありません。</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-gray-600">
              <th className="py-2">追跡番号</th>
              <th className="py-2">例外</th>
              <th className="py-2">発生状況</th>
              <th className="py-2">現在地</th>
            </tr>
          </thead>
          <tbody>
            {trackings.map((tracking) => (
              <tr
                key={tracking.trackingNumber}
                className="border-b border-gray-100"
              >
                <td className="py-2">
                  {/* 一覧を行き止まりにしない。ここから対応へ進む */}
                  <Link
                    to={`/tracking/manage?trackingNumber=${encodeURIComponent(tracking.trackingNumber)}`}
                    className="text-blue-600 hover:underline"
                  >
                    {tracking.trackingNumber}
                  </Link>
                </td>
                <td className="py-2">
                  {tracking.activeException?.urgent === true && (
                    <span className="mr-1 rounded bg-red-100 px-2 py-0.5 text-red-900">
                      緊急
                    </span>
                  )}
                  {tracking.activeException?.exceptionType}
                </td>
                <td className="py-2">
                  {tracking.activeException?.description}
                </td>
                <td className="py-2">{tracking.locationName}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
