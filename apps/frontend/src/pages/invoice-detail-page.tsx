import { Link, useParams } from "react-router-dom";

import { ChargeBasisPanel } from "../features/billing/components/charge-basis-panel";
import { useInvoice } from "../features/billing/queries";
import { formatRate, formatYen } from "../features/billing/money";
import { paymentStatusLabel } from "../features/billing/types";
import { formatBusinessDateTime } from "../lib/business-time";

/**
 * 請求書詳細（US21-5・US22-4）。
 *
 * <p><strong>金額を動かす操作を置かない</strong>（[ADR-027] 決定 4）。請求書は荷主へ出す
 * 約束であり、出したあとに黙って変わると請求の根拠が消える。訂正は US23（IT12）で
 * 「取り消して出し直す」形にする。
 *
 * <p><strong>割引率を出す</strong>（22-4）。額だけでは率を復元できない——基本料金と
 * 割引額から割り戻すと、丸めの分だけずれる。
 */
export function InvoiceDetailPage() {
  const { invoiceId = "" } = useParams();
  const { data: invoice, isLoading, error } = useInvoice(invoiceId);

  if (isLoading) {
    return <p>読み込み中です。</p>;
  }
  if (error !== null || invoice === undefined) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4">
        精算書が見つかりません。
        <Link className="ml-2 text-blue-700 underline" to="/billing">
          精算管理へ戻る
        </Link>
      </div>
    );
  }

  const discounted = invoice.baseAmount.value - (invoice.discountAmount?.value ?? 0);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">請求書詳細</h1>

      <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4">
        <div>
          <dt className="text-sm text-gray-600">請求番号</dt>
          <dd>{invoice.invoiceNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">予約番号</dt>
          <dd>
            <Link className="text-blue-700 underline" to={`/booking/${invoice.bookingId}`}>
              {invoice.bookingId}
            </Link>
          </dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">荷主</dt>
          <dd>{invoice.shipperName}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">状態</dt>
          <dd data-testid="payment-status">{paymentStatusLabel(invoice.paymentStatus)}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">発行日時</dt>
          <dd>{formatBusinessDateTime(invoice.issuedAt)}</dd>
        </div>
      </dl>

      <ChargeBasisPanel basis={invoice.basis} baseAmount={invoice.baseAmount} />

      <section aria-labelledby="breakdown-heading" className="space-y-2">
        <h2 id="breakdown-heading" className="text-lg font-semibold">
          金額内訳
        </h2>
        <table
          className="w-full border-collapse text-sm"
          data-testid="amount-breakdown"
        >
          <tbody>
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">基本運賃</th>
              <td className="px-3 py-2 text-right">{formatYen(invoice.baseAmount)}</td>
            </tr>
            {/* **割引率を出す**（22-4）。額だけでは率を復元できない */}
            {invoice.discountRate !== null && (
              <tr className="border-b border-gray-200">
                <th className="px-3 py-2 text-left">
                  法人割引（{formatRate(invoice.discountRate)}）
                </th>
                <td className="px-3 py-2 text-right">
                  -{formatYen(invoice.discountAmount)}
                </td>
              </tr>
            )}
            {invoice.discountRate !== null && (
              <tr className="border-b border-gray-200">
                <th className="px-3 py-2 text-left">割引後</th>
                <td className="px-3 py-2 text-right">
                  {formatYen({ value: discounted, currency: "JPY" })}
                </td>
              </tr>
            )}
            {invoice.cancellationFee !== null && (
              <tr className="border-b border-gray-200">
                <th className="px-3 py-2 text-left">
                  キャンセル料（{invoice.cancellationFee.bookingStatusLabel}・
                  {formatRate(invoice.cancellationFee.feeRate)}）
                </th>
                <td className="px-3 py-2 text-right">
                  {formatYen(invoice.cancellationFee.amount)}
                </td>
              </tr>
            )}
            {invoice.lineItems.map((item, index) => (
              <tr key={`${item.description}-${index}`} className="border-b border-gray-200">
                <th className="px-3 py-2 text-left">{item.description}</th>
                <td className="px-3 py-2 text-right">{formatYen(item.amount)}</td>
              </tr>
            ))}
            <tr className="border-b border-gray-200">
              <th className="px-3 py-2 text-left">
                {/* **税区分を出す**（[ADR-027] 決定 8 の改訂）。「消費税 ¥0」だけでは、
                    免税なのか計算漏れなのか読めない */}
                {invoice.taxExempt
                  ? '消費税（輸出免税）'
                  : `消費税（${formatRate(invoice.taxRate)}）`}
              </th>
              <td className="px-3 py-2 text-right">{formatYen(invoice.taxAmount)}</td>
            </tr>
            <tr className="border-b-2 border-gray-400 font-bold">
              <th className="px-3 py-2 text-left">合計</th>
              <td className="px-3 py-2 text-right">{formatYen(invoice.totalAmount)}</td>
            </tr>
          </tbody>
        </table>
      </section>

      {/* **通知は代替である。** メールの仕組みは無い（US23 以降）。
          書かないと、担当者は「発行したから荷主に届いた」と受け取って連絡をしない */}
      <p className="rounded border border-gray-300 bg-gray-50 p-3 text-sm">
        <strong>荷主へは自動で通知されません。</strong>
        {'請求内容は担当者からお伝えください。'}
      </p>

      {/* **訂正の手段が無いことを、発行後の画面でも言う**（IT11 レビュー 高。writer・user）。
          金額を動かさないのは決定 4 だが、「取り消して出し直す」手段も無い */}
      <p className="rounded border border-gray-300 bg-gray-50 p-3 text-sm">
        <strong>発行した請求書は訂正できません。</strong>
        {'誤って発行した場合は、経理責任者へ報告のうえ運用担当者へご連絡ください。'}
      </p>

      <Link className="text-blue-700 underline" to="/billing">
        精算管理へ戻る
      </Link>
    </div>
  );
}
