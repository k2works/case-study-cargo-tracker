import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import { adjustInvoice, fetchInvoice, formatMoney } from './api';

/**
 * S61 請求詳細・算出（UC17 / US21・US22）。
 *
 * <p><b>根拠を並べる。</b> 経理が確かめるのは「なぜこの額か」である。基本料金の
 * 区間と地域区分、割引率と契約番号、調整の理由と根拠の例外——金額だけでは
 * 確かめようがない。</p>
 *
 * <p><b>見積の概算と差額は出さない。</b> 見積は US01（IT14）で、いまは見積を
 * 経ない予約しかない（`quotedAmount` が `null`）。出すと「見積が無い」ことを
 * 「差額 0」と読み違える。</p>
 *
 * <p><b>調整は送信中を出す</b>（ui_design.md「複数ロールが触る集約の遷移」）。
 * 先行表示にすると、断られたときに金額が巻き戻る。</p>
 */
export function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams();
  const client = useQueryClient();
  const invoice = useQuery({
    queryKey: ['invoice', invoiceId],
    queryFn: () => fetchInvoice(invoiceId),
  });

  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [basisExceptionId, setBasisExceptionId] = useState('');

  const adjust = useMutation({
    mutationFn: () => adjustInvoice(invoiceId, {
      amount: Number(amount),
      reason,
      basisExceptionId: basisExceptionId.trim() === '' ? null : basisExceptionId.trim(),
    }),
    onSuccess: async () => {
      setAmount('');
      setReason('');
      setBasisExceptionId('');
      await client.invalidateQueries({ queryKey: ['invoice', invoiceId] });
    },
  });

  if (invoice.data?.state === 'pending') {
    return <output className={`${NOTICE} block`}>{invoice.data.message}</output>;
  }
  if (invoice.isError) {
    return <p role="alert" className={ALERT}>請求書を取得できませんでした</p>;
  }
  if (invoice.data?.state !== 'ready') {
    return <output className={`${NOTICE} block`}>読み込み中です</output>;
  }

  const view = invoice.data.value;

  return (
    <div>
      <h1 className={PAGE_TITLE}>請求書 {view.invoiceId}</h1>
      <p className="mt-1 text-sm text-gray-600">
        予約{' '}
        <Link className={LINK} to={`/bookings/${view.bookingId}`}>{view.bookingId}</Link>
        {' / '}
        {view.shipperName ?? '（削除済）'}（{view.shipperTypeLabel}）
        {' / '}
        <span>{view.statusLabel}</span>
        {' / '}
        算出 {formatBusinessDateTime(view.calculatedAt)}
      </p>
      {/* **一覧へ戻る口を置く。** 開いた先から戻れないと、ブラウザの戻るに頼る
          ことになる（共有画面のリンクもロールで出し分ける・IT7 の教訓）。 */}
      <p className="mt-1 text-sm">
        <Link className={LINK} to="/invoices">請求一覧へ戻る</Link>
      </p>

      <table className={`${TABLE} mt-4`}>
        <caption className={TABLE_CAPTION}>明細</caption>
        <thead>
          <tr>
            <th className={TH} scope="col">項目</th>
            <th className={TH} scope="col">金額</th>
            <th className={TH} scope="col">根拠</th>
          </tr>
        </thead>
        <tbody>
          {view.lineItems.map((line, index) => (
            <tr key={`${line.itemType}-${index}`}>
              <td className={TD}>{line.itemTypeLabel}</td>
              {/* **割引は合計を減らす向き**なので − を付けて出す（正典の S61 も
                  その形）。金額そのものは正で持つ——「割引額」は大きさであって、
                  符号で意味が変わる値ではない。調整は符号が向きを表すので
                  そのまま出す。 */}
              <td className={TD}>
                {formatMoney(
                  line.itemType === 'DISCOUNT' ? -line.amount : line.amount, line.currency)}
              </td>
              <td className={TD}>
                {line.description}
                {line.basisExceptionId !== null && (
                  <>
                    {' '}
                    {/* **根拠の例外へ飛べるようにする**（US28 §8 の受け側）。
                        ID を出すだけでは、探しに行くのは人の仕事になる。 */}
                    <Link
                      className={LINK}
                      to={`/tracking/exceptions?exceptionId=${encodeURIComponent(line.basisExceptionId)}`}
                    >
                      根拠の例外（{line.basisExceptionId}）
                    </Link>
                  </>
                )}
              </td>
            </tr>
          ))}
          <tr>
            <td className={TD}><b>合計</b></td>
            <td className={TD}><b>{formatMoney(view.totalAmount, view.currency)}</b></td>
            <td className={TD} />
          </tr>
        </tbody>
      </table>

      {/* 算出済のあいだだけ調整を受け付ける（US21 §受入基準 6）。押せるのに
          断られる操作を並べない。 */}
      {view.status === 'CALCULATED' && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">料金を調整する</h2>
          <p className="mt-1 text-sm text-gray-600">
            減額は負の数、補償費用は正の数で入れます。理由は必須です。
          </p>
          <form
            className="mt-3 grid gap-4 sm:grid-cols-3"
            onSubmit={(event) => {
              event.preventDefault();
              adjust.mutate();
            }}
          >
            <div>
              <label className={LABEL} htmlFor="adjust-amount">調整額</label>
              <input
                id="adjust-amount"
                className={FIELD}
                type="number"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </div>
            <div>
              <label className={LABEL} htmlFor="adjust-reason">理由</label>
              <input
                id="adjust-reason"
                className={FIELD}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            </div>
            <div>
              <label className={LABEL} htmlFor="adjust-basis">根拠の例外 ID（任意）</label>
              <input
                id="adjust-basis"
                className={FIELD}
                value={basisExceptionId}
                onChange={(event) => setBasisExceptionId(event.target.value)}
              />
            </div>
            <div className="sm:col-span-3">
              <button className={BUTTON_PRIMARY} type="submit" disabled={adjust.isPending}>
                調整を入れる
              </button>
              {adjust.isPending && (
                <output className={`${NOTICE} ml-3`}>送信中…</output>
              )}
            </div>
          </form>
          {adjust.isError && (
            <p role="alert" className={`${ALERT} mt-3`}>
              {adjust.error instanceof ApiError
                ? adjust.error.message : '調整を入れられませんでした'}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
