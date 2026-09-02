import { formatYen } from "../../billing/money";
import type { RouteCandidate } from "../estimate-types";
import { locationName } from "../location-name";
import { useLocations } from "../queries";

/**
 * ルート候補の一覧（受入基準 01-3）。
 *
 * <p><strong>4 項目をすべて出す</strong>——経由港・所要日数・概算料金・航海番号。
 * <strong>1 つ欠けても「候補が表示される」という字面は満たす</strong>ため、
 * 検査は 1 項目ずつ突き合わせる（IT11 Try 2）。
 */
export function RouteCandidateList({
  candidates,
}: Readonly<{ candidates: RouteCandidate[] }>) {
  const { data: locations = [] } = useLocations();

  if (candidates.length === 0) {
    return (
      <p className="rounded border border-gray-200 p-4 text-gray-700">
        ルート候補はありません。
      </p>
    );
  }

  return (
    <section aria-labelledby="candidates-heading" className="space-y-2">
      <h2 id="candidates-heading" className="text-lg font-semibold">
        ルート候補
      </h2>
      <ul className="space-y-2">
        {candidates.map((candidate, index) => (
          <li
            key={`${candidate.voyageNumber}-${index}`}
            className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4"
            data-testid="route-candidate"
          >
            <div>
              <span className="block text-sm text-gray-600">航海番号</span>
              {candidate.voyageNumber}
            </div>
            <div>
              <span className="block text-sm text-gray-600">経由港</span>
              {/* **直行も「経由港」の枠で言う。**枠ごと消すと、項目が欠けたのか
                  直行なのかを読み分けられない */}
              {candidate.transitPort === null
                ? "直行"
                : locationName(locations, candidate.transitPort)}
            </div>
            <div>
              <span className="block text-sm text-gray-600">所要日数</span>
              {candidate.transitDays} 日
            </div>
            <div>
              <span className="block text-sm text-gray-600">概算料金</span>
              {formatYen({ value: candidate.estimatedCost, currency: "JPY" })}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
