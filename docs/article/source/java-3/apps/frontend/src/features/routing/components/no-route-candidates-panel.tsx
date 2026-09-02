import { Link } from "react-router-dom";
import { ROUTING_CARGO_TYPE_LABELS, type RoutingCargoType } from "../types";

/** 積み替えを緩めるときの上限。既定は 2 回まで（[ADR-018]）。 */
const LOOSER_TRANSSHIPMENTS = 3;

type Props = {
  /** 実際に使われた条件。画面が送らなかった既定値も含む（サーバが返す）。 */
  appliedCargoType: RoutingCargoType;
  limited: boolean;
  deadline: string;
  maxTransshipments: number;
  earliestDeparture: string | null;
  onExtendDeadline: () => void;
  onLoosenTransshipments: () => void;
  /** 営業へ戻せる状態か。戻したあとの案内も状態で出し分ける。 */
  routingStatus: string;
  consultationPending: boolean;
  consultationSucceeded: boolean;
  onRequestConsultation: () => void;
};

/**
 * 候補が見つからなかったときの案内（US08・US10）。
 *
 * <p><strong>「見つかりませんでした」で終わらせない。</strong>この画面の中で行き止まりに
 * すると、荷主との条件交渉が始まらない（[ADR-020] 決定 7）。
 *
 * <p><strong>何が効いているかを示す。</strong>示さないと、経路設計者は期限だけを緩め続ける。
 */
export function NoRouteCandidatesPanel(props: Readonly<Props>) {
  return (
    <div className="space-y-3 rounded border border-amber-300 bg-amber-50 p-4">
      <h2 className="font-bold">
        期限内に到着できる経路が見つかりませんでした
      </h2>
      <div className="text-sm">
        <p>いま使った条件</p>
        <ul className="list-disc pl-5">
          <li>到着期限 {props.deadline} まで</li>
          <li>
            貨物種別 {ROUTING_CARGO_TYPE_LABELS[props.appliedCargoType]}
            {props.limited && <span>（運べる船が限られます）</span>}
          </li>
          <li>積み替え {props.maxTransshipments} 回まで</li>
          {props.earliestDeparture !== null && (
            <li>出発希望日 {props.earliestDeparture} 以降</li>
          )}
        </ul>
      </div>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={props.onExtendDeadline}
          className="rounded border border-gray-400 px-3 py-1"
        >
          到着期限を 1 週間延ばす
        </button>
        <button
          type="button"
          onClick={props.onLoosenTransshipments}
          className="rounded border border-gray-400 px-3 py-1"
          disabled={props.maxTransshipments >= LOOSER_TRANSSHIPMENTS}
        >
          積み替えを {LOOSER_TRANSSHIPMENTS} 回まで許す
        </button>
      </div>
      <p className="text-sm">
        それでも見つからない場合は、航海スケジュールにその区間の便が登録されているかを
        確認してください。
      </p>
      <Link to="/routing/voyages" className="text-blue-700 underline">
        航海スケジュールを見る
      </Link>

      {/* 「見つかりませんでした」で終わらせない。この画面の中で行き止まりにすると、
      荷主との条件交渉が始まらない（ADR-020 決定 7） */}
      {props.routingStatus === "ROUTING_REQUESTED" && (
        <div className="space-y-2 border-t border-amber-300 pt-3">
          <p className="text-sm">
            条件そのものを見直す必要がありそうなら、営業へ戻して荷主と協議してもらいます。
          </p>
          <button
            type="button"
            disabled={props.consultationPending}
            onClick={props.onRequestConsultation}
            className="rounded border border-gray-400 px-3 py-1 disabled:text-gray-400"
          >
            条件協議を依頼する
          </button>
          {/* 押した本人に効いたことを知らせる。件数の再取得を待つ形だと、
          押せたのかどうかが分からない */}
          {props.consultationSucceeded && (
            <output className="block rounded border border-green-300 bg-green-50 p-2 text-sm">
              営業へ戻しました。営業担当者のダッシュボードに表示されます。
            </output>
          )}
        </div>
      )}
      {props.routingStatus === "CONSULTATION_REQUESTED" && (
        <p className="text-sm text-gray-700">
          この予約は営業へ戻しています。条件が決まったら、もう一度この画面で経路を探します。
        </p>
      )}
    </div>
  );
}
