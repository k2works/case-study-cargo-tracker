import { Link, useParams } from "react-router-dom";

import { RouteCandidateList } from "../features/booking/components/route-candidate-list";
import { useEstimate } from "../features/booking/estimate-queries";
import { locationName } from "../features/booking/location-name";
import { useLocations } from "../features/booking/queries";
import { CARGO_TYPE_LABELS } from "../features/booking/types";

/**
 * 見積詳細（US01-4）。
 *
 * <p><strong>ここから予約へ進める。</strong>見積を作って終わりにすると、営業担当者は
 * 同じ条件を予約の画面で打ち直すことになり、そこで食い違いが生まれる（受入基準 01-7）。
 */
export function EstimateDetailPage() {
  const { estimateId = "" } = useParams();
  const { data: estimate, isLoading, error } = useEstimate(estimateId);
  const { data: locations = [] } = useLocations();

  if (isLoading) {
    return <p>読み込み中です。</p>;
  }
  if (error !== null || estimate === undefined) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4">
        見積が見つかりません。
        <Link className="ml-2 text-blue-700 underline" to="/booking/estimates">
          見積管理へ戻る
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">見積詳細</h1>

      <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-3">
        <div>
          <dt className="text-sm text-gray-600">見積番号</dt>
          <dd data-testid="estimate-number">{estimate.estimateNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">出発地</dt>
          <dd>{locationName(locations, estimate.originUnLocode)}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">目的地</dt>
          <dd>{locationName(locations, estimate.destinationUnLocode)}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">希望期限</dt>
          <dd>{estimate.arrivalDeadline}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">貨物種別</dt>
          <dd>{CARGO_TYPE_LABELS[estimate.cargoType]}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">重量</dt>
          <dd>{estimate.weightKg.toLocaleString("ja-JP")} kg</dd>
        </div>
      </dl>

      <RouteCandidateList candidates={estimate.candidates} />

      {/* **概算であることを書く。** 書かないと、荷主は見積の金額を確定額と受け取る */}
      <p className="rounded border border-gray-300 bg-gray-50 p-3 text-sm">
        <strong>概算料金は割引前・税別の金額です。</strong>
        {'実際の請求額は、輸送の実績（区間・重量）と契約割引をもとに精算時に確定します。'}
      </p>

      <div className="flex items-center gap-4">
        {/* **見積から予約へ渡す**（受入基準 01-7）。打ち直させると食い違いが生まれる */}
        <Link
          className="rounded bg-blue-600 px-4 py-2 text-white"
          to={`/booking/new?estimateId=${estimate.estimateId}`}
        >
          この見積で予約する
        </Link>
        <Link className="text-blue-700 underline" to="/booking/estimates">
          見積管理へ戻る
        </Link>
      </div>
    </div>
  );
}
