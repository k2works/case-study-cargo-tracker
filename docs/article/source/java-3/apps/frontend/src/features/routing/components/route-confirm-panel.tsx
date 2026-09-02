import { formatBusinessDateTime } from "../../../lib/business-time";
import { describeRoute, formatCost } from "../format";
import type { RouteCandidate } from "../types";

type Props = {
  candidate: RouteCandidate;
  originName: string;
  destinationName: string;
  pending: boolean;
  /** 確定に失敗した理由。**サーバの言葉をそのまま見せる**（何をすればよいかが変わるため）。 */
  failure: string | null;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * 選んだ経路を確定するかを確かめる（US09）。
 *
 * <p><strong>押した瞬間には確定しない。</strong>確定は予約の状態を動かし荷主への提示に
 * つながる。取り消す手段の無い操作を、一覧の行から直接起こさない。
 *
 * <p>確定すると何が起こるかを<strong>先に</strong>伝える。押したあとに気づくことにしない。
 */
export function RouteConfirmPanel({
  candidate,
  originName,
  destinationName,
  pending,
  failure,
  onConfirm,
  onCancel,
}: Readonly<Props>) {
  return (
    <section className="space-y-3 rounded border border-blue-300 bg-blue-50 p-4">
      <h2 className="font-bold">この経路で確定しますか</h2>
      <dl className="grid grid-cols-[8rem_1fr] gap-1 text-sm">
        <dt className="text-gray-600">経路</dt>
        <dd>{describeRoute(candidate, originName, destinationName)}</dd>
        <dt className="text-gray-600">航海</dt>
        <dd>
          {candidate.legs
            .map(
              (leg) =>
                `${leg.voyageNumber}（${leg.vesselName} / ${leg.carrierName}）`,
            )
            .join(" → ")}
        </dd>
        <dt className="text-gray-600">出発</dt>
        <dd>{formatBusinessDateTime(candidate.departureTime)}</dd>
        <dt className="text-gray-600">到着</dt>
        <dd>{formatBusinessDateTime(candidate.arrivalTime)}</dd>
        <dt className="text-gray-600">輸送日数</dt>
        <dd>{candidate.transitDays} 日</dd>
        <dt className="text-gray-600">費用の概算</dt>
        <dd>{formatCost(candidate.estimatedCost)}</dd>
      </dl>
      {/* 状態の変化を先に伝える。押したあとに気づくことにしない */}
      <p className="text-sm text-gray-700">
        確定すると、予約の状態が「経路提案中」になります。
        {/* 改行を空白と読ませない（日本語は語間を空けない） */}
        <strong>費用は概算です。正式な料金は精算時に確定します。</strong>
      </p>

      {failure !== null && (
        <p
          role="alert"
          className="rounded border border-red-200 bg-red-50 p-3 text-red-700"
        >
          {failure}
        </p>
      )}

      <div className="flex gap-3">
        <button
          type="button"
          disabled={pending}
          onClick={onConfirm}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:bg-gray-300"
        >
          この経路で確定する
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-gray-400 px-4 py-2 text-sm"
        >
          選び直す
        </button>
      </div>
    </section>
  );
}
