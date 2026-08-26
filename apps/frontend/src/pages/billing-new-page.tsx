import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ChargeBasisPanel } from "../features/billing/components/charge-basis-panel";
import { AdjustmentEvidence } from "../features/billing/components/adjustment-evidence";
import { useCalculateCharge, useChargeCalculation } from "../features/billing/queries";
import { formatRate, formatYen } from "../features/billing/money";
import type { LineItem } from "../features/billing/types";

/**
 * 料金算出（US21-2〜US21-6・US22-1〜US22-3）。
 *
 * <p><strong>この画面は保存しない</strong>（[ADR-027] 決定 3）。算出中の精算書は存在せず、
 * 確定操作をした時点で初めて精算書が発行される。下書きを持つと、下書きのまま忘れられた
 * 精算書が溜まる——それを見つける手段をまた作ることになる。
 *
 * <p><strong>金額の計算をしない。</strong>基本料金も割引も税も、サーバが計算して返した値を
 * 出すだけである（決定 2）。画面で計算すると丸めが 2 か所に分かれ、保存値と食い違う。
 * 調整を積んだあとの合計だけは、<strong>入力の結果をその場で見せるために画面で足す</strong>
 * ——確定するとサーバが計算し直した値に置き換わる。
 */
export function BillingNewPage() {
  const { bookingId = "" } = useParams();
  const navigate = useNavigate();
  const { data: calculation, isLoading, error } = useChargeCalculation(bookingId);
  const calculate = useCalculateCharge(bookingId);

  /** 画面で積んだ調整。**確定の瞬間にまとめて送る**（決定 3）。 */
  const [adjustments, setAdjustments] = useState<LineItem[]>([]);
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [inputError, setInputError] = useState<string | null>(null);

  if (isLoading) {
    return <p>読み込み中です。</p>;
  }
  if (error !== null || calculation === undefined) {
    return (
      <div role="alert" className="rounded border border-red-300 bg-red-50 p-4">
        料金を算出できませんでした。引取が終わっていないか、すでに精算書が発行されています。
      </div>
    );
  }

  const adjustmentTotal = adjustments.reduce((sum, item) => sum + item.amount.value, 0);
  const beforeTax =
    calculation.baseAmount.value -
    (calculation.discountAmount?.value ?? 0) +
    (calculation.cancellationFee?.amount.value ?? 0) +
    adjustmentTotal;
  const taxAmount = Math.round(beforeTax * calculation.taxRate);
  const total = beforeTax + taxAmount;

  function addAdjustment() {
    // **根拠の無い調整を作らせない**（決定 6）。金額だけ残ると、あとから誰も理由を言えない
    if (description.trim() === "") {
      setInputError("調整の内容を入力してください。");
      return;
    }
    const value = Number(amount);
    if (amount.trim() === "" || Number.isNaN(value)) {
      setInputError("調整額を数値で入力してください。");
      return;
    }
    setAdjustments((current) => [
      ...current,
      { description: description.trim(), amount: { value, currency: "JPY" } },
    ]);
    setDescription("");
    setAmount("");
    setInputError(null);
  }

  function confirm() {
    calculate.mutate(
      {
        adjustments: adjustments.map((item) => ({
          description: item.description,
          amountValue: item.amount.value,
        })),
      },
      {
        onSuccess: (invoice) => {
          navigate(`/billing/${invoice.invoiceId}`);
        },
      },
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">料金算出</h1>
      <p className="text-gray-700">
        予約番号 <strong>{calculation.bookingId}</strong> ／ 荷主{" "}
        <strong>{calculation.shipperName}</strong>
      </p>

      <ChargeBasisPanel basis={calculation.basis} baseAmount={calculation.baseAmount} />

      <section aria-labelledby="discount-heading" className="space-y-2">
        <h2 id="discount-heading" className="text-lg font-semibold">
          法人割引
        </h2>
        {/* **個人荷主には割引の欄そのものを出さない**（22-3）。0% を出すと
            「割引が 0 だった」に読め、契約が無いことと区別できない */}
        {calculation.discountRate === null ? (
          <p className="text-gray-700">
            個人のお客様のため、契約割引はありません。
          </p>
        ) : (
          <dl className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-3">
            <div>
              <dt className="text-sm text-gray-600">契約割引率</dt>
              <dd data-testid="discount-rate">{formatRate(calculation.discountRate)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-600">割引額</dt>
              <dd>{formatYen(calculation.discountAmount)}</dd>
            </div>
            <div>
              <dt className="text-sm text-gray-600">割引後</dt>
              <dd data-testid="discounted-amount">
                {formatYen({
                  value:
                    calculation.baseAmount.value - (calculation.discountAmount?.value ?? 0),
                  currency: "JPY",
                })}
              </dd>
            </div>
          </dl>
        )}
      </section>

      {calculation.cancellationFee !== null && (
        <section aria-labelledby="cancellation-heading" className="space-y-2">
          <h2 id="cancellation-heading" className="text-lg font-semibold">
            キャンセル料
          </h2>
          {/* **料率の根拠を出す**（US30-9）。IT9 は「算定していません」と書いていた */}
          <p data-testid="cancellation-fee" className="rounded border border-gray-200 p-4">
            {'キャンセルを申請した時点の状態が '}
            <strong>{calculation.cancellationFee.bookingStatusLabel}</strong>
            {` のため、料率 ${formatRate(calculation.cancellationFee.feeRate)} で `}
            <strong>{formatYen(calculation.cancellationFee.amount)}</strong>
            {' を加算します。'}
          </p>
        </section>
      )}

      <AdjustmentEvidence
        misroute={calculation.misroute}
        exceptions={calculation.exceptions}
      />

      <section aria-labelledby="adjustment-heading" className="space-y-3">
        <h2 id="adjustment-heading" className="text-lg font-semibold">
          料金調整
        </h2>
        {/* **金額は自動で決めない**（決定 6）。どれだけ減額するかは荷主との関係で
            決まる話であり、規則にできない。画面は根拠を出し、金額は担当者が入れる */}
        <p className="text-sm text-gray-600">
          {/* **改行を空白にしない**（JSX の改行はそのまま半角空白になる） */}
          {'減額は負の数、補償費用は正の数で入力してください。'}
          {'金額の判断は上の根拠をもとに行ってください——自動では決まりません。'}
        </p>

        {adjustments.length > 0 && (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-gray-300 bg-gray-50 text-left">
                <th className="px-3 py-2">内容</th>
                <th className="px-3 py-2 text-right">金額</th>
              </tr>
            </thead>
            <tbody>
              {adjustments.map((item, index) => (
                <tr key={`${item.description}-${index}`} className="border-b border-gray-200">
                  <td className="px-3 py-2">{item.description}</td>
                  <td className="px-3 py-2 text-right">{formatYen(item.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col text-sm">
            {'調整の内容'}
            <input
              className="rounded border border-gray-300 px-2 py-1"
              onChange={(event) => setDescription(event.target.value)}
              value={description}
            />
          </label>
          <label className="flex flex-col text-sm">
            {'調整額'}
            <input
              className="rounded border border-gray-300 px-2 py-1"
              onChange={(event) => setAmount(event.target.value)}
              type="number"
              value={amount}
            />
          </label>
          <button
            className="rounded bg-gray-700 px-3 py-1 text-white"
            onClick={addAdjustment}
            type="button"
          >
            調整を追加
          </button>
        </div>
        {inputError !== null && (
          <p className="text-red-700" role="alert">
            {inputError}
          </p>
        )}
      </section>

      <section aria-labelledby="total-heading" className="space-y-2">
        <h2 id="total-heading" className="text-lg font-semibold">
          合計
        </h2>
        <dl className="grid grid-cols-2 gap-2 rounded border border-gray-300 p-4">
          <dt>小計</dt>
          <dd className="text-right">{formatYen({ value: beforeTax, currency: "JPY" })}</dd>
          {/* **消費税は既定 10% で計算する**（決定 8）。税率を変える手段は US23 まで置かない */}
          <dt>消費税（{formatRate(calculation.taxRate)}）</dt>
          <dd className="text-right">{formatYen({ value: taxAmount, currency: "JPY" })}</dd>
          <dt className="font-bold">合計</dt>
          <dd className="text-right font-bold" data-testid="total-amount">
            {formatYen({ value: total, currency: "JPY" })}
          </dd>
        </dl>
      </section>

      {calculate.isError && (
        <p className="rounded border border-red-300 bg-red-50 p-3 text-red-800" role="alert">
          確定できませんでした。すでに精算書が発行されている可能性があります。
        </p>
      )}

      <div className="flex gap-3">
        <button
          className="rounded bg-blue-700 px-4 py-2 text-white disabled:bg-gray-400"
          disabled={calculate.isPending}
          onClick={confirm}
          type="button"
        >
          確定する
        </button>
        <button
          className="rounded border border-gray-400 px-4 py-2"
          onClick={() => navigate("/billing")}
          type="button"
        >
          精算管理へ戻る
        </button>
      </div>
      {/* **確定すると金額は動かなくなる**（決定 4）。押す前に言う
          ——請求書は荷主へ出す約束であり、出したあとに黙って変わると根拠が消える */}
      <p className="text-sm text-gray-600">
        確定すると精算書が発行され、<strong>金額は変更できなくなります</strong>
        {'。訂正が必要になった場合は、取り消して出し直すことになります。'}
      </p>
    </div>
  );
}
