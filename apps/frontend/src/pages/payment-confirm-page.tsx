import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { formatYen } from "../features/billing/money";
import { useConfirmPayment, useInvoice } from "../features/billing/queries";
import { PAYMENT_METHOD_LABELS } from "../features/billing/types";

/**
 * 入金の確認（US23-3・US23-4）。
 *
 * <p><strong>決済機関とは連携していない</strong>（代替）。接続先が無いため、経理担当者が
 * 通帳や入金明細を見て入れる。<strong>代替であることを画面に書く</strong>——書かないと、
 * 「連携が壊れている」と受け取って待ち続ける。
 *
 * <p><strong>入れた根拠を残す。</strong>「入金済」だけでは、いつ・いくら・どの振込かを
 * 誰も追えない。あとから荷主に問われたときに答えられるのは、入金日と参照番号である。
 */
export function PaymentConfirmPage() {
  const { invoiceId = "" } = useParams();
  const navigate = useNavigate();
  const { data: invoice, isLoading, error } = useInvoice(invoiceId);
  const confirm = useConfirmPayment(invoiceId);

  const [paidAt, setPaidAt] = useState("");
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  /**
   * 請求額との差額を確かめたか。
   *
   * <p><strong>一致するのが普通である。</strong>違うのは、振込手数料を差し引かれたか
   * 一部入金かのどちらかで、どちらも経理担当者が気づくべきことである
   * ——気づかないまま「入金済」で閉じると、不足のまま完了した請求が積み上がる
   * （IT12 レビュー・user 高 1）。
   */
  const [differenceAcknowledged, setDifferenceAcknowledged] = useState(false);
  /** 請求額を初期値にする（打ち直しは桁を間違える機会を作るだけである）。 */
  const [seededInvoiceId, setSeededInvoiceId] = useState("");

  if (invoice !== undefined && seededInvoiceId !== invoice.invoiceNumber) {
    setSeededInvoiceId(invoice.invoiceNumber);
    setAmount(String(invoice.totalAmount.value));
  }

  if (isLoading) {
    return <p>読み込み中です。</p>;
  }
  if (error !== null || invoice === undefined) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4">
        請求書が見つかりません。
        <Link className="ml-2 text-blue-700 underline" to="/billing">
          精算管理へ戻る
        </Link>
      </div>
    );
  }

  // 請求額との差額。**プラスは過入金、マイナスは不足**
  const difference = amount === "" ? 0 : Number(amount) - invoice.totalAmount.value;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">入金の確認</h1>

      <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4">
        <div>
          <dt className="text-sm text-gray-600">請求番号</dt>
          <dd>{invoice.invoiceNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">荷主</dt>
          <dd>{invoice.shipperName}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">請求金額</dt>
          <dd data-testid="billed-amount">{formatYen(invoice.totalAmount)}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">支払期限</dt>
          <dd>{invoice.dueDate ?? "—"}</dd>
        </div>
      </dl>

      {/* **代替であることを書く**（受入基準 23-3）。 */}
      <p className="rounded border border-gray-300 bg-gray-50 p-3 text-sm">
        <strong>決済機関とは連携していません。</strong>
        {'通帳・入金明細を確認して入力してください。入力した内容は請求書に残ります。'}
      </p>

      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          // **差額があれば一度止める**（受入基準 23-3 の実務）。振込手数料の差引も
          // 一部入金も日常的に起きる——黙って通すと、不足のまま完了した請求が残る
          if (difference !== 0 && !differenceAcknowledged) {
            setDifferenceAcknowledged(true);
            return;
          }
          confirm.mutate(
            {
              amountValue: Number(amount),
              paidAt,
              method,
              transactionReference: reference === "" ? null : reference,
            },
            { onSuccess: () => navigate(`/billing/${invoice.invoiceNumber}`) },
          );
        }}
      >
        <div>
          <label className="block text-sm" htmlFor="paidAt">
            入金日
          </label>
          {/* **日付である**——通帳に時刻は無い */}
          <input
            id="paidAt"
            type="date"
            required
            value={paidAt}
            onChange={(event) => setPaidAt(event.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label className="block text-sm" htmlFor="amount">
            入金額
          </label>
          <input
            id="amount"
            type="number"
            required
            min={1}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          />
        </div>
        <div>
          <label className="block text-sm" htmlFor="method">
            入金方法
          </label>
          <select
            id="method"
            value={method}
            onChange={(event) => setMethod(event.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          >
            {Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm" htmlFor="reference">
            {/* **任意であることをラベルに書く。**相殺には振込番号が無い */}
            参照番号（任意）
          </label>
          {/* **必須にしない。**相殺には振込番号が無い。あとから裏を取る手がかりである */}
          <input
            id="reference"
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            className="rounded border border-gray-300 px-3 py-2"
          />
        </div>

        {/* **差額をその場で見せる。**画面上部の請求額と見比べさせるより、
            差そのものを出すほうが早く気づける */}
        {difference !== 0 && amount !== "" && (
          <p
            role="alert"
            className="rounded border border-amber-300 bg-amber-50 p-3 text-sm"
            data-testid="payment-difference"
          >
            <strong>
              {`請求額と ${formatYen({ value: Math.abs(difference), currency: "JPY" })} の差があります（${difference < 0 ? "不足" : "過入金"}）。`}
            </strong>
            {'振込手数料の差引か、一部入金ではありませんか。'}
            {differenceAcknowledged
              ? 'このまま確認する場合は、もう一度 [確認する] を押してください。'
              : '確かめてから [確認する] を押してください。'}
          </p>
        )}

        {confirm.error !== null && (
          <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">
            入金を確認できませんでした。入力内容をお確かめください。
          </p>
        )}

        <div className="flex items-center gap-4">
          <button
            type="submit"
            className="rounded bg-blue-600 px-4 py-2 text-white"
            disabled={confirm.isPending}
          >
            確認する
          </button>
          <Link className="text-blue-700 underline" to={`/billing/${invoice.invoiceNumber}`}>
            請求書へ戻る
          </Link>
        </div>
      </form>
    </div>
  );
}
