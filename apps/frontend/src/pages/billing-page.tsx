import { Link, useSearchParams } from "react-router-dom";

import {
  useInvoices,
  useOverdueInvoices,
  useUnbilledBookings,
} from "../features/billing/queries";
import { paymentStatusLabel } from "../features/billing/types";
import { formatBusinessDateTime } from "../lib/business-time";
import { formatYen } from "../features/billing/money";

/**
 * 精算管理（US21・US22）。**経理担当者が毎朝開く画面である。**
 *
 * <p><strong>2 つの待ち行列を 1 画面に置く。</strong>
 * <ul>
 *   <li>料金を算出していない引取済の予約——<strong>これから仕事をする相手</strong>
 *   <li>発行済みの精算書——<strong>済ませた仕事</strong>
 * </ul>
 *
 * <p>分けずに 1 つの一覧にすると、済ませたものが混ざって「今日やること」が読めなくなる。
 * IT10 の一覧（誤配の件数から辿る）と同じ考え方である。
 *
 * <p><strong>経理担当者は他に気づく手段を持たない。</strong>メールの仕組みは無い
 * （通知は US23 以降）。ここに出ていないものは、誰にも気づかれない。
 */
/**
 * 一覧の行に付ける印。
 *
 * <p><strong>入れ子の三項をやめて、判断の順序を読める形にする。</strong>
 * キャンセルと誤配は同時に起こりうる——そのときはキャンセルを優先する
 * （料率の根拠が要るのはキャンセルのほうである）。
 */
function unbilledKind(booking: {
  cancelled: boolean
  misrouted: boolean
  shipperType: string
}): string {
  if (booking.cancelled) {
    return 'unbilled-cancelled'
  }
  if (booking.misrouted) {
    return 'unbilled-misrouted'
  }
  return booking.shipperType === 'INDIVIDUAL'
    ? 'unbilled-individual'
    : 'unbilled-corporate'
}

export function BillingPage() {
  const [params, setParams] = useSearchParams();
  // **ダッシュボードの件数から、そのまま対象へ来られる**（受入基準 23-5 の代替）。
  // 件数を出すだけでは仕事は進まない
  const overdueOnly = params.get("filter") === "overdue";
  const { data: unbilled = [], isLoading: loadingUnbilled } = useUnbilledBookings();
  // **締めの作業を表計算から引き上げる**（US38）。探す語と発行月は画面が持ち、
  // 絞り込み・件数・合計はサーバが同じ条件で答える
  // **絞り込みは URL に載せる。**載せないと、1 通開いて戻った瞬間に条件が消える
  // ——月次照合は「その月の 180 件を上から開く」作業であり、1 通目で消えると
  // 180 回選び直すことになる（IT16 レビュー 高 2）。`filter=overdue` と同じ形にする
  const keyword = params.get("keyword") ?? "";
  const issuedMonth = params.get("issuedMonth") ?? "";
  const setCriteria = (next: { keyword?: string; issuedMonth?: string }) => {
    const updated = new URLSearchParams(params);
    for (const [key, value] of Object.entries(next)) {
      if (value === undefined || value === "") {
        updated.delete(key);
      } else {
        updated.set(key, value);
      }
    }
    setParams(updated, { replace: true });
  };
  // **開いた先へ条件を持って行く。**戻るときに同じ絞り込みへ戻れる
  const detailQuery = params.toString() === "" ? "" : `?${params.toString()}`;
  const { data: searchResult, isLoading: loadingAll } = useInvoices({
    keyword,
    issuedMonth,
  });
  const allInvoices = searchResult?.invoices ?? [];
  const { data: overdueInvoices = [], isLoading: loadingOverdue } = useOverdueInvoices();
  const invoices = overdueOnly ? overdueInvoices : allInvoices;
  const loadingInvoices = overdueOnly ? loadingOverdue : loadingAll;
  // **超過の判定はサーバの答えをそのまま使う**——画面で日付を比べ直すと、
  // 業務タイムゾーンの扱いが 2 か所に分かれる
  const overdueNumbers = new Set(overdueInvoices.map((invoice) => invoice.invoiceNumber));

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold">精算管理</h1>

      <section aria-labelledby="unbilled-heading" className="space-y-3">
        <h2 id="unbilled-heading" className="text-lg font-semibold">
          {'料金を算出していない引取済の予約 '}
          <span className="text-sm font-normal text-gray-600">
            {unbilled.length} 件
          </span>
        </h2>
        {/* **古い順に並ぶ**（サーバが並べる）。待たせている案件が上に来る
            ——新しい順だと、いちばん待たせている荷主への請求が下に沈む */}
        <p className="text-sm text-gray-600">
{'最後に荷役があった順に並んでいます。上から順に料金を算出してください。'}
          <strong>{'荷役の記録が無い予約（経路が決まる前のキャンセル）は「—」と出て、最後に並びます。'}</strong>
        </p>

        {loadingUnbilled && <p>読み込み中です。</p>}
        {!loadingUnbilled && unbilled.length === 0 && (
          <p className="rounded border border-gray-200 p-4 text-gray-700">
            料金の算出を待っている予約はありません。
          </p>
        )}
        {!loadingUnbilled && unbilled.length > 0 && (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 bg-gray-50 text-left">
                <th className="px-3 py-2">予約番号</th>
                <th className="px-3 py-2">荷主</th>
                <th className="px-3 py-2">区間</th>
                {/* **列名を実態に合わせる**（IT11 レビュー 中。user）。
                    値は最後に荷役があった日時であり、引取の日時とは限らない
                    ——「いつからお待たせしているか」の判断に使う */}
                <th className="px-3 py-2">最終荷役日時</th>
                <th className="px-3 py-2">特記</th>
              </tr>
            </thead>
            <tbody>
              {unbilled.map((booking) => (
                <tr
                  key={booking.bookingId}
                  className="border-b border-gray-200"
                  data-testid={unbilledKind(booking)}
                >
                  <td className="px-3 py-2">
                    <Link
                      className="text-blue-700 underline"
                      to={`/billing/new/${booking.bookingId}`}
                    >
                      {booking.bookingId}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{booking.shipperName}</td>
                  <td className="px-3 py-2">
                    {booking.originName} → {booking.destinationName}
                  </td>
                  <td className="px-3 py-2">
                    {booking.lastHandlingAt === null
                      ? "—"
                      : formatBusinessDateTime(booking.lastHandlingAt)}
                  </td>
                  <td className="px-3 py-2">
                    {/* **根拠のある案件は先に知らせる。** 開いてから気づくと、
                        調整の要否を判断し直すことになる */}
                    {booking.cancelled && (
                      <span className="rounded bg-gray-200 px-2 py-1">キャンセル</span>
                    )}
                    {booking.misrouted && (
                      <span className="ml-1 rounded bg-amber-100 px-2 py-1 text-amber-900">
                        誤配あり
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section
        aria-labelledby="invoices-heading"
        className="space-y-3"
        data-testid={overdueOnly ? "overdue-invoices" : "issued-invoices"}
      >
        <h2 id="invoices-heading" className="text-lg font-semibold">
          {overdueOnly ? "支払期限を過ぎた請求 " : "発行済みの精算書 "}
          <span className="text-sm font-normal text-gray-600">
            {overdueOnly ? invoices.length : (searchResult?.totalCount ?? 0)} 件
          </span>
        </h2>

        {/* **締めの作業を表計算から引き上げる**（US38）。
            期限超過の一覧は別の絞りなので、検索欄は出さない */}
        {!overdueOnly && (
          <div className="space-y-2 rounded border border-gray-200 p-3">
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex flex-col text-sm">
                <span className="text-gray-700">請求番号・荷主名・予約番号</span>
                <input
                  type="search"
                  value={keyword}
                  onChange={(event) => setCriteria({ keyword: event.target.value })}
                  placeholder="伊藤商事 / INV-2026 / BKG-2026"
                  className="mt-1 rounded border border-gray-300 px-3 py-2"
                />
              </label>
              <label className="flex flex-col text-sm">
                <span className="text-gray-700">発行月</span>
                <input
                  type="month"
                  value={issuedMonth}
                  onChange={(event) => setCriteria({ issuedMonth: event.target.value })}
                  className="mt-1 rounded border border-gray-300 px-3 py-2"
                />
              </label>
              {(keyword !== "" || issuedMonth !== "") && (
                <button
                  type="button"
                  onClick={() => setCriteria({ keyword: "", issuedMonth: "" })}
                  className="rounded border border-gray-300 px-3 py-2 text-sm hover:bg-gray-100"
                >
                  条件を消す
                </button>
              )}
            </div>
            {/* **合計はサーバが数える。**画面で足し上げると、上限で切った瞬間に
                「見えている分だけの合計」に化ける。取り消し済みは入らない */}
            <p className="text-sm text-gray-800">
              {"合計 "}
              <strong>
                {formatYen({
                  value: searchResult?.totalAmount ?? 0,
                  currency: searchResult?.currency ?? "JPY",
                })}
              </strong>
              <span className="ml-2 text-gray-600">（取り消し済みを除く）</span>
            </p>
            {searchResult?.truncated === true && (
              <p className="text-sm text-amber-800">
                {`${searchResult.limit} 件まで表示しています。条件を絞ってください。`}
              </p>
            )}
          </div>
        )}

        {/* **絞っていることを言い、外す手段を同じ場所に置く。**言わないと、
            発行したはずの請求書が「消えた」と読まれる */}
        {overdueOnly && (
          <p className="text-sm text-gray-700">
            {"支払期限を過ぎたものだけを出しています。"}
            <Link className="ml-2 text-blue-700 underline" to="/billing">
              すべての精算書を見る
            </Link>
          </p>
        )}

        {loadingInvoices && <p>読み込み中です。</p>}
        {!loadingInvoices && invoices.length === 0 && (
          <p className="rounded border border-gray-200 p-4 text-gray-700">
            {overdueOnly
              ? "支払期限を過ぎた請求はありません。"
              : "発行済みの精算書はありません。"}
          </p>
        )}
        {!loadingInvoices && invoices.length > 0 && (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 bg-gray-50 text-left">
                <th className="px-3 py-2">請求番号</th>
                <th className="px-3 py-2">予約番号</th>
                <th className="px-3 py-2">荷主</th>
                <th className="px-3 py-2">合計</th>
                <th className="px-3 py-2">状態</th>
                <th className="px-3 py-2">支払期限</th>
                <th className="px-3 py-2">発行日時</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr key={invoice.invoiceId} className="border-b border-gray-200">
                  <td className="px-3 py-2">
                    <Link
                      className="text-blue-700 underline"
                      data-testid="invoice-link"
                      to={`/billing/${invoice.invoiceId}${detailQuery}`}
                    >
                      {invoice.invoiceNumber}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{invoice.bookingId}</td>
                  <td className="px-3 py-2">{invoice.shipperName}</td>
                  <td className="px-3 py-2 text-right">{formatYen(invoice.totalAmount)}</td>
                  <td className="px-3 py-2">
                    {/* **取り消しは状態に混ぜない**（[ADR-028] 決定 4）。それでも
                        一覧では見分けが要る——未入金の一覧に取り消し済みが並ぶと、
                        払われていない請求として催促してしまう */}
                    {paymentStatusLabel(invoice.paymentStatus)}
                    {invoice.voidedAt !== null && (
                      <span className="ml-1 text-red-700">（取消済）</span>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    {/* **期限を過ぎた行は、全件の一覧でも見分けられるようにする。**
                        状態列は保存上ずっと「未入金」であり（[ADR-028] 決定 5——超過は
                        日付で判定する）、超過はここでしか気づけない
                        （IT12 レビュー・user 中） */}
                    {invoice.dueDate ?? "—"}
                    {overdueNumbers.has(invoice.invoiceNumber) && (
                      <span className="ml-1 font-medium text-red-700">（超過）</span>
                    )}
                  </td>
                  <td className="px-3 py-2">{formatBusinessDateTime(invoice.issuedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
