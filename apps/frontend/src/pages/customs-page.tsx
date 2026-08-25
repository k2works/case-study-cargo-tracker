import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuthStore } from "../stores/auth-store";
import {
  useCustomsDeclarations,
  useCustomsStatuses,
  useOverdueCustoms,
} from "../features/customs/queries";
import type { CustomsSearchCriteria } from "../features/customs/types";

/**
 * 通関申告の一覧（US29-6・US29-7）。
 *
 * **留め置かれている貨物から手を付けられること**が、この画面の値打ちである。
 * 追跡管理者は毎朝ここを開き、上から順に処理する。
 *
 * **警告の判定はサーバが行う**（`heldOverdue`）。画面で日付を引き算すると、
 * 利用者の端末の時計と時差の分だけ結果が変わる。業務日付は業務タイムゾーンの
 * Clock で判断する。
 *
 * **件数から対象一覧へ辿れる**（横断規約）。件数だけ出しても仕事は進まない。
 */
const EMPTY: CustomsSearchCriteria = {
  bookingId: "",
  trackingNumber: "",
  status: "",
};

/** 留置からの日数の表示。留置でなければ数えていないので「-」を出す。 */
function heldDaysLabel(heldDays: number | null): string {
  return heldDays === null ? "-" : `${heldDays} 日`;
}

export function CustomsPage() {
  const [form, setForm] = useState<CustomsSearchCriteria>(EMPTY);
  const [criteria, setCriteria] = useState<CustomsSearchCriteria>(EMPTY);
  /** 留置 3 日超だけに絞っているか。**サーバの判定をそのまま使う**。 */
  const [overdueOnly, setOverdueOnly] = useState(false);

  /**
   * 申告を出すのは荷役作業員だけ（[ADR-025] 決定 6）。追跡管理者は状態を更新する側で
   * あり、申告そのものは出さない。**押せない操作を見せない**。
   */
  const user = useAuthStore((state) => state.user);
  const canRegister = user?.roles.includes("ROLE_HANDLER") === true;

  const { data: statuses = [] } = useCustomsStatuses();
  const { data: declarations = [], isLoading } = useCustomsDeclarations(criteria);

  /**
   * **件数はサーバに聞く。**
   *
   * いま画面に出ている行から数えると、貨物 ID で検索した直後や「通関済」に絞った状態で
   * バナーが消える。**絞り込んだら警告が消えた**は、警告そのものへの信用を失わせる。
   */
  const { data: overdue } = useOverdueCustoms();
  const overdueCount = overdue?.count ?? 0;
  const shown = overdueOnly
    ? declarations.filter((declaration) => declaration.heldOverdue)
    : declarations;

  function search(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setOverdueOnly(false);
    setCriteria(form);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">通関申告一覧</h1>
        {canRegister && (
          <Link
            to="/customs/new"
            className="rounded bg-blue-600 px-4 py-2 text-white"
          >
            新規申告
          </Link>
        )}
      </div>

      {/* 件数を出すだけでは仕事は進まない。ここから対象だけに絞り込める */}
      {overdueCount > 0 && (
        <p className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">
          <button
            type="button"
            onClick={() => setOverdueOnly(true)}
            className="underline"
          >
            留置のまま 3 日を超えた申告が {overdueCount} 件あります
          </button>
        </p>
      )}

      {overdueOnly && (
        <p className="text-sm text-gray-600">
          {'留置 3 日超だけを出しています。'}
          <button
            type="button"
            onClick={() => setOverdueOnly(false)}
            className="ml-2 text-blue-600 underline"
          >
            すべて表示する
          </button>
        </p>
      )}

      <form onSubmit={search} className="flex flex-wrap items-end gap-4">
        <div>
          <label
            htmlFor="bookingId"
            className="block text-sm font-medium text-gray-700"
          >
            貨物 ID
          </label>
          <input
            id="bookingId"
            value={form.bookingId}
            onChange={(event) =>
              setForm({ ...form, bookingId: event.target.value })
            }
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label
            htmlFor="trackingNumber"
            className="block text-sm font-medium text-gray-700"
          >
            追跡番号
          </label>
          <input
            id="trackingNumber"
            value={form.trackingNumber}
            onChange={(event) =>
              setForm({ ...form, trackingNumber: event.target.value })
            }
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label
            htmlFor="status"
            className="block text-sm font-medium text-gray-700"
          >
            通関状態
          </label>
          {/* 選択肢はサーバが持つ。画面に対訳表を置くと、状態を足したときに直す場所が増える */}
          <select
            id="status"
            value={form.status}
            onChange={(event) =>
              setForm({ ...form, status: event.target.value })
            }
            className="mt-1 rounded border border-gray-300 px-3 py-2"
          >
            <option value="">すべて</option>
            {statuses.map((choice) => (
              <option key={choice.status} value={choice.status}>
                {choice.label}
              </option>
            ))}
          </select>
        </div>
        <button
          type="submit"
          className="rounded bg-blue-600 px-4 py-2 text-white"
        >
          検索
        </button>
      </form>

      {isLoading && <p className="text-sm text-gray-600">読み込んでいます…</p>}

      {!isLoading && shown.length === 0 ? (
        <p className="text-sm text-gray-600">通関申告はありません。</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-gray-600">
              <th className="py-2">申告番号</th>
              <th className="py-2">貨物 ID</th>
              <th className="py-2">追跡番号</th>
              <th className="py-2">申告日時</th>
              <th className="py-2">状態</th>
              <th className="py-2">経過</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((declaration) => (
              <tr
                key={declaration.declarationId}
                className="border-b border-gray-100"
              >
                <td className="py-2">
                  {/* 一覧を行き止まりにしない。ここから状態の更新へ進む */}
                  <Link
                    to={`/customs/${declaration.declarationId}`}
                    className="text-blue-600 hover:underline"
                  >
                    {declaration.declarationNumber}
                  </Link>
                </td>
                <td className="py-2">{declaration.bookingId}</td>
                <td className="py-2">{declaration.trackingNumber}</td>
                <td className="py-2">{declaration.declaredAt}</td>
                <td className="py-2">{declaration.statusLabel}</td>
                <td className="py-2">
                  {declaration.heldOverdue ? (
                    // 色だけに頼らない。テキストラベルを必ず併記する
                    <span className="rounded bg-red-100 px-2 py-0.5 text-red-900">
                      ⚠ 3 日超（{declaration.heldDays} 日）
                    </span>
                  ) : (
                    heldDaysLabel(declaration.heldDays)
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
