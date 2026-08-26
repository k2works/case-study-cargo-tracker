import { Link } from "react-router-dom";

import { useEstimates } from "../features/booking/estimate-queries";
import { CARGO_TYPE_LABELS } from "../features/booking/types";

/**
 * 見積管理（US01）。**営業担当者が荷主と話しながら開く画面である。**
 *
 * <p><strong>見積番号を最初に出す。</strong>荷主は電話で「見積番号 EST-…」と言う
 * ——UUID を読み上げることはできない（[ADR-028] 決定 7）。
 */
export function EstimateListPage() {
  const { data: estimates = [], isLoading } = useEstimates();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">見積管理</h1>
        <Link
          className="rounded bg-blue-600 px-4 py-2 text-white"
          to="/booking/estimates/new"
        >
          新規見積
        </Link>
      </div>

      {isLoading && <p>読み込み中です。</p>}
      {!isLoading && estimates.length === 0 && (
        <p className="rounded border border-gray-200 p-4 text-gray-700">
          見積はまだありません。
        </p>
      )}
      {!isLoading && estimates.length > 0 && (
        <table className="w-full border-collapse text-sm" data-testid="estimate-list">
          <thead>
            <tr className="border-b border-gray-300 bg-gray-50 text-left">
              <th className="px-3 py-2">見積番号</th>
              <th className="px-3 py-2">出発地</th>
              <th className="px-3 py-2">目的地</th>
              <th className="px-3 py-2">希望期限</th>
              <th className="px-3 py-2">貨物種別</th>
              <th className="px-3 py-2">候補</th>
            </tr>
          </thead>
          <tbody>
            {estimates.map((estimate) => (
              <tr key={estimate.estimateId} className="border-b border-gray-200">
                <td className="px-3 py-2">
                  <Link
                    className="text-blue-700 underline"
                    to={`/booking/estimates/${estimate.estimateId}`}
                  >
                    {estimate.estimateNumber}
                  </Link>
                </td>
                <td className="px-3 py-2">{estimate.originUnLocode}</td>
                <td className="px-3 py-2">{estimate.destinationUnLocode}</td>
                <td className="px-3 py-2">{estimate.arrivalDeadline}</td>
                <td className="px-3 py-2">{CARGO_TYPE_LABELS[estimate.cargoType]}</td>
                <td className="px-3 py-2">{estimate.candidates.length} 件</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
