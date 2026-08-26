import { Link } from "react-router-dom";

import { useInvoices, useUnbilledBookings } from "../features/billing/queries";
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
export function BillingPage() {
  const { data: unbilled = [], isLoading: loadingUnbilled } = useUnbilledBookings();
  const { data: invoices = [], isLoading: loadingInvoices } = useInvoices();

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold">精算管理</h1>

      <section aria-labelledby="unbilled-heading" className="space-y-3">
        <h2 id="unbilled-heading" className="text-lg font-semibold">
          料金を算出していない引取済の予約
          <span className="ml-2 text-sm font-normal text-gray-600">
            {unbilled.length} 件
          </span>
        </h2>
        {/* **古い順に並ぶ**（サーバが並べる）。待たせている案件が上に来る
            ——新しい順だと、いちばん待たせている荷主への請求が下に沈む */}
        <p className="text-sm text-gray-600">
          引取が終わった順に並んでいます。上から順に料金を算出してください。
        </p>

        {loadingUnbilled ? (
          <p>読み込み中です。</p>
        ) : unbilled.length === 0 ? (
          <p className="rounded border border-gray-200 p-4 text-gray-700">
            料金の算出を待っている予約はありません。
          </p>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 bg-gray-50 text-left">
                <th className="px-3 py-2">予約番号</th>
                <th className="px-3 py-2">荷主</th>
                <th className="px-3 py-2">区間</th>
                <th className="px-3 py-2">引取日時</th>
                <th className="px-3 py-2">特記</th>
              </tr>
            </thead>
            <tbody>
              {unbilled.map((booking) => (
                <tr
                  key={booking.bookingId}
                  className="border-b border-gray-200"
                  data-testid={
                    booking.cancelled
                      ? "unbilled-cancelled"
                      : booking.misrouted
                        ? "unbilled-misrouted"
                        : booking.shipperType === "INDIVIDUAL"
                          ? "unbilled-individual"
                          : "unbilled-corporate"
                  }
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
                    {booking.claimedAt === null
                      ? "—"
                      : formatBusinessDateTime(booking.claimedAt)}
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

      <section aria-labelledby="invoices-heading" className="space-y-3">
        <h2 id="invoices-heading" className="text-lg font-semibold">
          発行済みの精算書
          <span className="ml-2 text-sm font-normal text-gray-600">
            {invoices.length} 件
          </span>
        </h2>

        {loadingInvoices ? (
          <p>読み込み中です。</p>
        ) : invoices.length === 0 ? (
          <p className="rounded border border-gray-200 p-4 text-gray-700">
            発行済みの精算書はありません。
          </p>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 bg-gray-50 text-left">
                <th className="px-3 py-2">請求番号</th>
                <th className="px-3 py-2">予約番号</th>
                <th className="px-3 py-2">荷主</th>
                <th className="px-3 py-2">合計</th>
                <th className="px-3 py-2">状態</th>
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
                      to={`/billing/${invoice.invoiceId}`}
                    >
                      {invoice.invoiceNumber}
                    </Link>
                  </td>
                  <td className="px-3 py-2">{invoice.bookingId}</td>
                  <td className="px-3 py-2">{invoice.shipperName}</td>
                  <td className="px-3 py-2 text-right">{formatYen(invoice.totalAmount)}</td>
                  <td className="px-3 py-2">{paymentStatusLabel(invoice.paymentStatus)}</td>
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
