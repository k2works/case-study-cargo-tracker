import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  useCustomsDeclaration,
  useCustomsStatuses,
  useUpdateCustomsStatus,
} from "../features/customs/queries";
import { ApiError } from "../lib/api-client";
import { useAuthStore } from "../stores/auth-store";

/**
 * 通関申告の詳細と状態の更新（US29-2・US29-8）。
 *
 * **状態を更新できるのは追跡管理者だけ**（[ADR-025] 決定 6）。荷役作業員は自分が出した
 * 申告の行方を追うために開くが、更新はできない。**押せない操作を見せない**——見せて
 * 403 にすると、現場は毎回そこで詰まる。守るのはサーバであり、画面はその写しである。
 *
 * **理由は必須**（US29-2）。空で通すと、監査の履歴が「誰かが変えた」だけになる。
 */
export function CustomsDetailPage() {
  const { declarationId } = useParams();
  const id = Number(declarationId);

  const { data: declaration, isLoading } = useCustomsDeclaration(
    Number.isNaN(id) ? null : id,
  );
  const { data: statuses = [] } = useCustomsStatuses();
  const update = useUpdateCustomsStatus(id);

  const user = useAuthStore((state) => state.user);
  const canUpdate = user?.roles.includes("ROLE_TRACKER") === true;

  const [status, setStatus] = useState("");
  const [reason, setReason] = useState("");
  const [updated, setUpdated] = useState(false);

  function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setUpdated(false);
    update.mutate(
      { status, reason },
      {
        onSuccess: () => {
          setUpdated(true);
          setReason("");
        },
      },
    );
  }

  const failure = update.error instanceof ApiError ? update.error.message : null;

  if (isLoading) {
    return <p className="text-sm text-gray-600">読み込んでいます…</p>;
  }

  if (declaration === undefined) {
    return (
      <div className="space-y-4">
        <p role="alert" className="text-sm text-red-800">
          通関申告が見つかりません。
        </p>
        <Link to="/customs" className="text-blue-600 hover:underline">
          通関申告一覧に戻る
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">通関申告詳細</h1>
        <Link to="/customs" className="text-blue-600 hover:underline">
          通関申告一覧に戻る
        </Link>
      </div>

      <section className="rounded border border-gray-200 p-4">
        <dl className="grid gap-2 md:grid-cols-2">
          <div>
            <dt className="text-sm text-gray-600">申告番号</dt>
            <dd className="font-medium">{declaration.declarationNumber}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">状態</dt>
            <dd className="font-medium">
              {declaration.statusLabel}
              {declaration.heldOverdue && (
                // 色だけに頼らない。テキストラベルを併記する
                <span className="ml-2 rounded bg-red-100 px-2 py-0.5 text-sm text-red-900">
                  ⚠ 3 日超（{declaration.heldDays} 日）
                </span>
              )}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">貨物 ID</dt>
            <dd>{declaration.bookingId}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">追跡番号</dt>
            <dd>{declaration.trackingNumber}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">申告日時</dt>
            <dd>{declaration.declaredAt}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">通関完了日時</dt>
            <dd>{declaration.clearedAt ?? "―"}</dd>
          </div>
        </dl>
      </section>

      {canUpdate && (
        <section className="space-y-4 rounded border border-gray-200 p-4">
          <h2 className="text-lg font-semibold text-gray-900">状態の更新</h2>

          {updated && (
            <p
              role="status"
              className="rounded border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-900"
            >
              更新しました。
            </p>
          )}

          {failure !== null && (
            <p
              role="alert"
              className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900"
            >
              {failure}
            </p>
          )}

          <form onSubmit={submit} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label
                  htmlFor="status"
                  className="block text-sm font-medium text-gray-700"
                >
                  新しい状態
                </label>
                {/* 選択肢はサーバが持つ。画面に対訳表を置かない */}
                <select
                  id="status"
                  required
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                  className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
                >
                  <option value="">選んでください</option>
                  {statuses
                    .filter((choice) => choice.status !== declaration.status)
                    .map((choice) => (
                      <option key={choice.status} value={choice.status}>
                        {choice.label}
                      </option>
                    ))}
                </select>
              </div>
              <div>
                <label
                  htmlFor="reason"
                  className="block text-sm font-medium text-gray-700"
                >
                  変更の理由
                </label>
                <input
                  id="reason"
                  required
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  className="mt-1 w-full rounded border border-gray-300 px-3 py-2"
                />
              </div>
            </div>

            <p className="text-sm text-gray-600">
              理由は<strong>履歴に残ります</strong>。あとから「なぜこの状態になったか」を
              読むのは、荷主に説明する担当者です。
            </p>

            <button
              type="submit"
              disabled={update.isPending}
              className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
            >
              状態を更新する
            </button>
          </form>
        </section>
      )}

      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-gray-900">状態変更履歴</h2>
        <table aria-label="状態変更履歴" className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 text-gray-600">
              <th className="py-2">日時</th>
              <th className="py-2">変更者</th>
              <th className="py-2">変更</th>
              <th className="py-2">理由</th>
            </tr>
          </thead>
          <tbody>
            {/* 新しいものを上に。担当者が知りたいのは「直近に何があったか」である */}
            {[...declaration.history].reverse().map((change) => (
              <tr
                key={`${change.changedAt}-${change.toStatus}`}
                className="border-b border-gray-100"
              >
                <td className="py-2">{change.changedAt}</td>
                <td className="py-2">{change.changedBy}</td>
                <td className="py-2">
                  {change.fromStatus === change.toStatus
                    ? "（登録）"
                    : `${change.fromStatusLabel} → ${change.toStatusLabel}`}
                </td>
                <td className="py-2">{change.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
