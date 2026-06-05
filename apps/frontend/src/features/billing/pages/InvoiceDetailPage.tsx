import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  applyDiscount,
  billingStatusLabel,
  fetchInvoice,
  invoiceLineTypeLabel,
  issueInvoice,
  recordPayment,
  type Invoice,
} from '../api/billingApi';

/**
 * S23 請求詳細・算出画面（US21 / US22 / US23、ROLE_ACCOUNTANT）。
 *
 * <p>iteration_plan-7 §UI 設計の S23 に対応。発行前は CALCULATED で内訳を確認、
 * 発行後（INVOICED）は invoice_number / payment_due を表示する。</p>
 *
 * <p>本画面（IT7 タスク 2.6）は表示専用。確定（PATCH /finalize）/ 例外調整（PATCH /adjust）/
 * 精算書発行（POST /issue）の操作 UI は Task 3.x（割引）/ 4.x（発行・入金）で順次追加する。</p>
 */
export default function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams<{ invoiceId: string }>();
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [error, setError] = useState<'NOT_FOUND' | 'OTHER' | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [applying, setApplying] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchInvoice(invoiceId)
      .then((data) => {
        if (!cancelled) {
          setInvoice(data);
          setError(null);
        }
      })
      .catch((e: Error) => {
        if (!cancelled) {
          setInvoice(null);
          setError(e.message === 'NOT_FOUND' ? 'NOT_FOUND' : 'OTHER');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [invoiceId, reloadKey]);

  const handleApplyDiscount = useCallback(async () => {
    setApplying(true);
    try {
      await applyDiscount(invoiceId);
      setReloadKey((k) => k + 1);
    } catch {
      setError('OTHER');
    } finally {
      setApplying(false);
    }
  }, [invoiceId]);

  const handleIssue = useCallback(async () => {
    setIssuing(true);
    try {
      await issueInvoice(invoiceId);
      setReloadKey((k) => k + 1);
    } catch {
      setError('OTHER');
    } finally {
      setIssuing(false);
    }
  }, [invoiceId]);

  const handleRecordPayment = useCallback(
    async (totalAmount: string, currency: string) => {
      setPaying(true);
      try {
        await recordPayment(invoiceId, {
          paidAmount: totalAmount,
          currency,
          paymentMethod: 'MANUAL',
        });
        setReloadKey((k) => k + 1);
      } catch {
        setError('OTHER');
      } finally {
        setPaying(false);
      }
    },
    [invoiceId],
  );

  if (error === 'NOT_FOUND') {
    return (
      <div className="mx-auto max-w-4xl px-4 py-6">
        <p className="text-red-600">請求書が見つかりません: {invoiceId}</p>
      </div>
    );
  }
  if (error === 'OTHER') {
    return (
      <div className="mx-auto max-w-4xl px-4 py-6">
        <p className="text-red-600">請求書の取得中にエラーが発生しました</p>
      </div>
    );
  }
  if (!invoice) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-6">
        <p className="text-gray-500">読み込み中…</p>
      </div>
    );
  }

  const discountAmountNum = Number(invoice.discountAmount);
  const hasDiscount = !Number.isNaN(discountAmountNum) && discountAmountNum > 0;
  const canApplyDiscount =
    invoice.billingStatus === 'CALCULATED' && !hasDiscount && !applying;
  const canIssue = invoice.billingStatus === 'CALCULATED' && !issuing;
  const canRecordPayment =
    (invoice.billingStatus === 'INVOICED' || invoice.billingStatus === 'OVERDUE') && !paying;

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <h1 className="text-2xl font-bold mb-4">
        請求詳細 - {invoice.invoiceId}
      </h1>

      <section className="mb-6 grid grid-cols-2 gap-y-2 text-sm">
        <dt className="text-gray-600">予約 ID</dt>
        <dd>{invoice.bookingId}</dd>

        <dt className="text-gray-600">荷主 ID</dt>
        <dd>
          {invoice.shipperId}
          {hasDiscount && (
            <span className="ml-2 rounded bg-green-100 px-2 py-0.5 text-green-800 text-xs">
              割引適用済
            </span>
          )}
        </dd>

        <dt className="text-gray-600">状態</dt>
        <dd>
          <span className="rounded bg-blue-100 px-2 py-1 text-blue-800 text-xs">
            {billingStatusLabel(invoice.billingStatus)}
          </span>
        </dd>

        {invoice.invoiceNumber && (
          <>
            <dt className="text-gray-600">請求書番号</dt>
            <dd>{invoice.invoiceNumber}</dd>
          </>
        )}

        {invoice.paymentDue && (
          <>
            <dt className="text-gray-600">支払期限</dt>
            <dd>{invoice.paymentDue}</dd>
          </>
        )}

        {invoice.paidAt && (
          <>
            <dt className="text-gray-600">入金日時</dt>
            <dd>{invoice.paidAt}</dd>
          </>
        )}
      </section>

      {hasDiscount && (
        <section className="mb-6 rounded border border-green-200 bg-green-50 p-3 text-sm">
          <h2 className="font-semibold text-green-800 mb-2">割引前後の対比</h2>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <p className="text-xs text-gray-600">割引前（basic_amount）</p>
              <p className="font-bold">{formatAmount(invoice.basicAmount)} {invoice.currency}</p>
            </div>
            <div>
              <p className="text-xs text-gray-600">割引額</p>
              <p className="font-bold text-red-600">
                -{formatAmount(invoice.discountAmount)} {invoice.currency}
              </p>
            </div>
            <div>
              <p className="text-xs text-gray-600">割引後（total_amount）</p>
              <p className="font-bold text-green-700">
                {formatAmount(invoice.totalAmount)} {invoice.currency}
              </p>
            </div>
          </div>
        </section>
      )}

      {canApplyDiscount && (
        <section className="mb-6">
          <button
            type="button"
            onClick={handleApplyDiscount}
            disabled={applying}
            className="rounded bg-blue-600 px-4 py-2 text-white text-sm font-semibold hover:bg-blue-700 disabled:opacity-50"
          >
            {applying ? '適用中…' : '割引を適用（法人荷主のみ）'}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            ※ ShipperInfoAcl で荷主契約を取得し CorporateDiscountPolicy で算出します。INDIVIDUAL 荷主の場合は割引額 0 で確定します。
          </p>
        </section>
      )}

      {canIssue && (
        <section className="mb-6">
          <button
            type="button"
            onClick={handleIssue}
            disabled={issuing}
            className="rounded bg-indigo-600 px-4 py-2 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50"
          >
            {issuing ? '発行中…' : '精算書を発行'}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            ※ InvoiceNumberGenerator で INV-YYYYMMDD-XXXX を採番、PaymentDuePolicy で支払期限（発行日 + 30 日）を確定します。
          </p>
        </section>
      )}

      {canRecordPayment && (
        <section className="mb-6">
          <button
            type="button"
            onClick={() => handleRecordPayment(invoice.totalAmount, invoice.currency)}
            disabled={paying}
            className="rounded bg-emerald-600 px-4 py-2 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50"
          >
            {paying ? '記録中…' : `入金を記録（${formatAmount(invoice.totalAmount)} ${invoice.currency}）`}
          </button>
          <p className="mt-2 text-xs text-gray-500">
            ※ IT7 は完全一致のみ受理。決済方法は MANUAL、外部参照は未設定で記録します（IT8 で webhook 統合予定）。
          </p>
        </section>
      )}

      <section className="mb-6">
        <h2 className="text-lg font-semibold mb-2">料金内訳</h2>
        <table className="w-full text-sm border-collapse">
          <thead>
            <tr className="border-b border-gray-300">
              <th className="text-left py-2 px-2">#</th>
              <th className="text-left py-2 px-2">種別</th>
              <th className="text-left py-2 px-2">摘要</th>
              <th className="text-right py-2 px-2">金額（{invoice.currency}）</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lines.map((line) => (
              <tr key={line.lineSeq} className="border-b border-gray-200">
                <td className="py-2 px-2">{line.lineSeq}</td>
                <td className="py-2 px-2">{invoiceLineTypeLabel(line.lineType)}</td>
                <td className="py-2 px-2">{line.description}</td>
                <td className="py-2 px-2 text-right">{formatAmount(line.amount)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-gray-400 font-bold">
              <td colSpan={3} className="py-2 px-2 text-right">
                合計（total_amount）
              </td>
              <td className="py-2 px-2 text-right text-lg">
                {formatAmount(invoice.totalAmount)}
              </td>
            </tr>
          </tfoot>
        </table>
      </section>

      <section className="text-xs text-gray-500">
        <p>
          作成: {invoice.createdAt} / 更新: {invoice.updatedAt}
        </p>
        <p className="mt-2 text-gray-400">
          ※ 例外調整入力・確定操作・精算書発行は IT7 Task 3.x / 4.x で実装予定です。
        </p>
      </section>
    </div>
  );
}

/** BigDecimal 文字列を 3 桁区切りに整形（マイナス値・小数点未満は表示）。 */
function formatAmount(value: string): string {
  const num = Number(value);
  if (Number.isNaN(num)) return value;
  return num.toLocaleString('ja-JP');
}
