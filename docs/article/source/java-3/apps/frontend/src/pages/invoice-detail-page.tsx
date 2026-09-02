import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";

import { ChargeBasisPanel } from "../features/billing/components/charge-basis-panel";
import { useInvoice, useVoidInvoice } from "../features/billing/queries";
import { formatRate, formatYen } from "../features/billing/money";
import { paymentStatusLabel } from "../features/billing/types";
import { formatBusinessDateTime } from "../lib/business-time";

/**
 * 請求書詳細（US21-5・US22-4）。
 *
 * <p><strong>金額を動かす操作を置かない</strong>（[ADR-027] 決定 4）。請求書は荷主へ出す
 * 約束であり、出したあとに黙って変わると請求の根拠が消える。<strong>訂正は取り消して
 * 出し直す</strong>（[ADR-028] 決定 3）——消さずに残すのは、DB を直すのが監査に
 * 耐えないからである。
 *
 * <p><strong>割引率を出す</strong>（22-4）。額だけでは率を復元できない——基本料金と
 * 割引額から割り戻すと、丸めの分だけずれる。
 */
export function InvoiceDetailPage() {
  const { invoiceId = "" } = useParams();
  /**
   * 一覧へ戻る先。
   *
   * <p>**絞り込みを持ったまま戻る。**月次照合は「その月の 180 件を上から開く」
   * 作業であり、1 通目で条件が消えると 180 回選び直すことになる。
   */
  const [listParams] = useSearchParams();
  const backToList =
    listParams.toString() === "" ? "/billing" : `/billing?${listParams.toString()}`;
  const { data: invoice, isLoading, error } = useInvoice(invoiceId);
  const revoke = useVoidInvoice(invoiceId);
  const [revoking, setRevoking] = useState(false);
  const [reason, setReason] = useState("");

  if (isLoading) {
    return <p>読み込み中です。</p>;
  }
  if (error !== null || invoice === undefined) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4">
        精算書が見つかりません。
        {/*
          **絞り込みを保って戻る。**条件を落として戻すと、月次照合で 1 通目を
          開いた瞬間に条件が消える（IT16 レビュー 高 2）
        */}
        <Link className="ml-2 text-blue-700 underline" to={backToList}>
          精算管理へ戻る
        </Link>
      </div>
    );
  }

  const discounted = invoice.baseAmount.value - (invoice.discountAmount?.value ?? 0);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">請求書詳細</h1>

      {/* **紙に出す宛名と発行元。**画面では場所を取るだけなので、印刷のときだけ出す。
          振込先の口座は、まだシステムが持っていない（[13 章の申し送り]）——
          持っていないものを紙に書けないので、そこは運用で添える */}
      <div className="hidden print:block">
        <p className="text-lg font-bold">{`${invoice.shipperName} 御中`}</p>
        <p className="mt-4 text-sm">CargoTracker 株式会社</p>
        <p className="text-sm">{`請求番号 ${invoice.invoiceNumber}`}</p>
        <p className="text-sm">
          {`発行日 ${formatBusinessDateTime(invoice.issuedAt)} ／ 支払期限 ${invoice.dueDate ?? "—"}`}
        </p>
      </div>

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
        <div>
          {/* **期限を出す**（受入基準 23-1）。出さないと、催促の判断ができない */}
          <dt className="text-sm text-gray-600">支払期限</dt>
          <dd data-testid="due-date">{invoice.dueDate ?? "—"}</dd>
        </div>
      </dl>

      {/* **取り消したことを最初に言う。**金額の話より先である */}
      {invoice.voidedAt !== null && (
        <p
          role="alert"
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
          data-testid="void-reason"
        >
          <strong>この請求書は取り消されています。</strong>
          {`理由: ${invoice.voidReason ?? "（記録なし）"}（`}
          {formatBusinessDateTime(invoice.voidedAt)}
          {"）"}
        </p>
      )}

      {/* **入れた根拠を残す**（受入基準 23-3）。「入金済」だけでは、いつ・いくら・
          どの振込かを誰も追えない */}
      {invoice.payment !== null && (
        <dl
          className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4"
          data-testid="payment-record"
        >
          <div>
            <dt className="text-sm text-gray-600">入金日</dt>
            {/* サーバが業務の暦で決めた日付。**ここで解釈し直さない**——端末の
                タイムゾーンで読み直すと 1 日ずれる */}
            <dd>{invoice.payment.paidAt}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">入金額</dt>
            <dd>{formatYen(invoice.payment.amount)}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">入金方法</dt>
            <dd>{invoice.payment.methodLabel}</dd>
          </div>
          <div>
            <dt className="text-sm text-gray-600">参照番号</dt>
            <dd>{invoice.payment.transactionReference ?? "—"}</dd>
          </div>
        </dl>
      )}

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
      <p className="print-hide rounded border border-gray-300 bg-gray-50 p-3 text-sm">
        <strong>荷主へは自動で通知されません。</strong>
        {'請求内容は担当者からお伝えください。'}
      </p>

      {/* **入金の確認が手作業であることを言う**（受入基準 23-3 の代替）。
          書かないと、経理担当者は「連携が壊れている」と受け取って待ち続ける */}
      {invoice.paymentStatus === "PENDING" && invoice.voidedAt === null && (
        <div className="print-hide space-y-2 rounded border border-gray-300 bg-gray-50 p-3 text-sm">
          <p>
            <strong>決済機関とは連携していません。</strong>
            {'入金は手で確認します（通帳・入金明細を見て入力してください）。'}
          </p>
          <Link
            className="inline-block rounded bg-blue-600 px-4 py-2 text-white"
            to={`/billing/${invoice.invoiceNumber}/payment`}
          >
            入金を確認する
          </Link>
        </div>
      )}

      {/* **訂正は取り消して出し直す**（[ADR-028] 決定 3）。金額は動かさない（決定 4） */}
      {invoice.voidedAt === null && invoice.paymentStatus === "PENDING" && (
        <section className="print-hide space-y-2 rounded border border-gray-300 p-3 text-sm">
          <p>
            <strong>発行した請求書の金額は変えられません。</strong>
            {'誤って発行した場合は取り消し、正しい内容で出し直してください。'}
          </p>
          {revoking ? (
            <form
              className="space-y-2"
              onSubmit={(event) => {
                event.preventDefault();
                revoke.mutate(reason, { onSuccess: () => setRevoking(false) });
              }}
            >
              <label className="block" htmlFor="void-reason">
                取り消しの理由
              </label>
              {/* **理由は必須。**残らないと、あとから二重発行の失敗と区別できない */}
              <input
                id="void-reason"
                required
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className="w-full rounded border border-gray-300 px-3 py-2"
              />
              <button
                type="submit"
                className="rounded bg-red-600 px-4 py-2 text-white"
                disabled={revoke.isPending}
              >
                取り消しを記録する
              </button>
            </form>
          ) : (
            <button
              type="button"
              className="rounded border border-red-600 px-4 py-2 text-red-700"
              onClick={() => setRevoking(true)}
            >
              取り消す
            </button>
          )}
          {revoke.error !== null && (
            <p role="alert" className="text-red-700">
              取り消せませんでした。
            </p>
          )}
        </section>
      )}

      <div className="print-hide flex items-center gap-4">
        {/* **画面の数字がそのまま紙になる**（経理担当者の申し送り③）。印刷が無いと、
            数字を書き写して表計算で作ることになり、システムの金額と実際に送った
            請求書が食い違い始める */}
        <button
          type="button"
          className="rounded border border-gray-400 px-4 py-2"
          onClick={() => globalThis.print()}
        >
          印刷する
        </button>
        <Link className="text-blue-700 underline" to={backToList}>
          精算管理へ戻る
        </Link>
      </div>
    </div>
  );
}
