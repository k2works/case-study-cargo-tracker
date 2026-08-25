import { Link } from "react-router-dom";
import { useOpenExceptionList } from "../features/tracking/queries";
import { useAuthStore } from "../stores/auth-store";

/**
 * 未解決の例外がある貨物の一覧（横断規約）。
 *
 * **件数の遷移先である。** 件数を出すだけでは仕事は進まない——「3 件ある」と分かっても、
 * どの貨物かが分からなければ次にすることが無い（[IT7 の学び](../../docs/development/retrospective-7.md)）。
 *
 * **一覧から個別の管理画面へ行ける。** 一覧が行き止まりだと、番号を書き写して
 * 打ち直すことになる。
 *
 * **営業も読む**（IT9 返済枠 0.9）。荷主は公開の追跡照会で「ご依頼元の営業担当へ」と
 * 案内されるため、営業が何も知らないままでは案内が行き止まりになる。ただし営業は
 * 貨物状態の管理画面を開けない（[ADR-008]）ので、**リンク先をロールで出し分ける**。
 * 開けない画面へ誘導すると、押した先で断られる。
 *
 * **並び順はサーバが決める**（緊急を先に、次に発生の古い順）。画面で並べ替えると、
 * 並びの規則が 2 か所になる。
 */
export function TrackingExceptionsPage() {
  const { data: trackings = [], isLoading } = useOpenExceptionList();
  const user = useAuthStore((state) => state.user);
  const canManage =
    user?.roles.includes("ROLE_TRACKER") === true ||
    user?.roles.includes("ROLE_HANDLER") === true;

  /**
   * 予約詳細を開けるか（`App.tsx` のルートガードと揃える）。
   *
   * <p><strong>開けない画面へ誘導しない。</strong>IT10 のレビューで、誤配に最初に気づく
   * 追跡管理者がこのリンクを押すと /403 に飛んでいた。予約詳細は読み取りで開いたが、
   * <strong>ここの判定を揃えておかないと、次にロールが増えたとき同じことが起きる</strong>。
   *
   * <p>開けないロールには、代わりに<strong>次に何が起きるか</strong>を伝える。誤配を直すのは
   * 経路設計者であり、その手元には気づく手段がある（経路設計ダッシュボードの件数）。
   */
  const canOpenBooking =
    user?.roles.some((role) =>
      ["ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_HANDLER"].includes(
        role,
      ),
    ) === true;

  /** 対応へ進む先。営業は管理画面を開けないので、公開の照会へ送る。 */
  const detailPathOf = (trackingNumber: string) =>
    canManage
      ? `/tracking/manage?trackingNumber=${encodeURIComponent(trackingNumber)}`
      : `/tracking/${encodeURIComponent(trackingNumber)}`;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">未解決の例外</h1>
        {canManage && (
          <Link to="/tracking/manage" className="text-blue-600 hover:underline">
            貨物状態の管理に戻る
          </Link>
        )}
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
              <th className="py-2">発生日時</th>
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
                    to={detailPathOf(tracking.trackingNumber)}
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
                  {tracking.activeException?.label}
                </td>
                {/* いつから放置されているか。これが無いと、どれから手を付けるか決まらない */}
                <td className="py-2">{tracking.activeException?.occurredAt}</td>
                <td className="py-2">
                  {tracking.activeException?.description}
                  {/* **誤配は経路設計者が直す**（US28・[ADR-026] 決定 6）。
                      気づく人（追跡管理者）と直す人が違うため、予約へ渡す導線が要る
                      ——ここから辿れないと「気づいたが何もできない」で終わる */}
                  {tracking.activeException?.exceptionType === "MISROUTE" &&
                    (canOpenBooking ? (
                      <>
                        {" "}
                        <Link
                          to={`/booking/${encodeURIComponent(tracking.bookingId)}`}
                          className="text-blue-600 hover:underline"
                        >
                          予約を開く（経路の組み直し）
                        </Link>
                      </>
                    ) : (
                      <span className="text-gray-600">
                        {" "}
                        （経路設計者が組み直します）
                      </span>
                    ))}
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
