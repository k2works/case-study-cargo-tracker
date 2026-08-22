import { Link } from "react-router-dom";
import { formatBusinessDateTime } from "../../../lib/business-time";
import { withReturnTo } from "../../../lib/return-path";
import { describeRoute, formatCost } from "../format";
import type { RouteCandidate } from "../types";

type Props = {
  candidates: RouteCandidate[];
  totalCount: number | undefined;
  originName: string;
  destinationName: string;
  /** 選ばせてよいか。予約の条件と違う条件で探しているあいだは選ばせない（US10）。 */
  selectable: boolean;
  returnTo: string;
  onChoose: (candidate: RouteCandidate) => void;
};

/**
 * 経路候補の一覧（US08）。
 *
 * <p>並びは推奨順（[ADR-018]）であり、<strong>画面は並べ替えない</strong>。
 * 費用は概算であり請求金額ではない。
 */
export function RouteCandidateTable({
  candidates,
  totalCount,
  originName,
  destinationName,
  selectable,
  returnTo,
  onChoose,
}: Readonly<Props>) {
  return (
    <div className="space-y-2">
      <h2 className="font-bold">候補 {totalCount} 件（推奨順）</h2>
      <p className="text-sm text-gray-600">
        直行便を最優先に並べています。到着の早さだけで並べているわけではありません。
      </p>
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-gray-300 text-left">
            <th className="py-2">順位</th>
            <th>経路</th>
            <th>航海</th>
            <th>船 / 運送会社</th>
            <th>出発</th>
            <th>到着</th>
            <th>輸送日数</th>
            <th>費用の概算</th>
            <th>選択</th>
          </tr>
        </thead>
        <tbody>
          {candidates.map((candidate) => (
            <tr key={candidate.rank} className="border-b border-gray-200">
              <td className="py-2">
                {candidate.rank}
                {candidate.direct && (
                  <span className="ml-1 rounded bg-green-100 px-1 text-xs text-green-800">
                    直行
                  </span>
                )}
              </td>
              <td>{describeRoute(candidate, originName, destinationName)}</td>
              <td className="space-x-1">
                {candidate.voyageNumbers.map((number) => (
                  <Link
                    key={number}
                    // 条件ごと戻り先を渡す。渡さないと、航海を確かめて戻った人は
                    // どの予約を見ていたか分からない場所に出る
                    to={withReturnTo(`/routing/voyages/${number}`, returnTo)}
                    className="text-blue-700 underline"
                  >
                    {number}
                  </Link>
                ))}
              </td>
              <td className="whitespace-nowrap">
                {candidate.legs.map((leg) => (
                  <div key={`${leg.voyageNumber}-${leg.fromUnLocode}`}>
                    {leg.vesselName}
                    <span className="ml-1 text-gray-600">
                      / {leg.carrierName}
                    </span>
                  </div>
                ))}
              </td>
              <td>{formatBusinessDateTime(candidate.departureTime)}</td>
              <td>{formatBusinessDateTime(candidate.arrivalTime)}</td>
              <td>{candidate.transitDays} 日</td>
              <td>{formatCost(candidate.estimatedCost)}</td>
              <td>
                {/* 押した瞬間に確定しない。経路の確定は予約の状態を動かし、
                荷主への提示につながる。取り消す手段の無い操作を行から直接起こさない */}
                {selectable ? (
                  <button
                    type="button"
                    onClick={() => onChoose(candidate)}
                    className="rounded bg-blue-600 px-3 py-1 text-xs text-white"
                  >
                    この経路を選ぶ
                  </button>
                ) : (
                  <span className="text-xs text-gray-500">—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="text-sm text-gray-600">
        費用は<strong>概算</strong>です。正式な料金は精算時に確定します。
      </p>
    </div>
  );
}
