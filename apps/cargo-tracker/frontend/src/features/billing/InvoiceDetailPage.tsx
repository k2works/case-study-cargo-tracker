import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_DANGER,
  BUTTON_PRIMARY,
  BUTTON_SECONDARY,
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
import { businessLocalToInstant, formatBusinessDateTime } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import {
  adjustInvoice,
  fetchInvoice,
  formatMoney,
  issueInvoice,
  recordPayment,
  reverseAdjustment,
  voidInvoice,
  voidPayment,
} from './api';
import type { InvoiceLineView } from './api';

/**
 * 根拠の種類。**接頭辞で決まる**——通関申告は `IMP-`、例外はそれ以外。
 *
 * <p>種類ごとに飛び先が違う。一律に例外一覧へ送ると、留置の保管料を根拠にした
 * 調整が「該当なし」に着く（IT13 のレビュー 高）。</p>
 */
function isCustomsDeclaration(basisId: string): boolean {
  return basisId.startsWith('IMP-');
}

function basisLinkOf(basisId: string): string {
  return isCustomsDeclaration(basisId)
    ? `/customs/${encodeURIComponent(basisId)}`
    : `/tracking/exceptions?exceptionId=${encodeURIComponent(basisId)}`;
}

function basisLabelOf(basisId: string): string {
  return isCustomsDeclaration(basisId) ? '根拠の通関申告' : '根拠の例外';
}

/**
 * S61 請求詳細・算出（UC17 / US21・US22）。
 *
 * <p><b>根拠を並べる。</b> 経理が確かめるのは「なぜこの額か」である。基本料金の
 * 区間と地域区分、割引率と契約番号、調整の理由と根拠の例外——金額だけでは
 * 確かめようがない。</p>
 *
 * <p><b>見積の概算と差額は、見積から作った予約でだけ出す。</b> 見積を経ない
 * 予約では `quotedAmount` が `null` になる——出すと「見積が無い」ことを
 * 「差額 0」と読み違える。</p>
 *
 * <p><b>差は普通のことである。</b> 見積は候補経路、請求は実際に通った区間で
 * 数えるので、式と料率が同じでも金額は変わる。困るのは差ではなく説明できない
 * ことなので、概算・請求・差額を並べて出す（ui_design.md S61）。</p>
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

  /**
   * 調整の向き。**符号を打たせない**（IT14 引き継ぎ C）。「−」を打ち忘れた減額が
   * 補償費用として積まれるのを、入り口で防ぐ。
   */
  const [direction, setDirection] = useState<'DEDUCTION' | 'COMPENSATION'>('DEDUCTION');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [basisExceptionId, setBasisExceptionId] = useState('');

  const [paidAt, setPaidAt] = useState('');
  const [paymentVoidReason, setPaymentVoidReason] = useState('');
  const [voidReason, setVoidReason] = useState('');

  const issue = useMutation({
    mutationFn: () => issueInvoice(invoiceId),
    onSuccess: () => client.invalidateQueries({ queryKey: ['invoice', invoiceId] }),
  });

  const pay = useMutation({
    mutationFn: (amount: number) => recordPayment(invoiceId, {
      // **請求額をそのまま送る。** 打たせると、打ち間違いが一部入金として
      // 断られ、経理は「なぜ通らないのか」を金額から探すことになる。
      amount,
      // 入金日は日付で聞き、業務タイムゾーンの正午として送る——UTC で作ると
      // 時差の分だけ前日の入金になる時間帯ができる。
      paidAt: businessLocalToInstant(`${paidAt}T12:00`),
    }),
    onSuccess: async () => {
      setPaidAt('');
      await client.invalidateQueries({ queryKey: ['invoice', invoiceId] });
    },
  });

  const revokePayment = useMutation({
    mutationFn: (paymentId: string) => voidPayment(invoiceId, paymentId, paymentVoidReason),
    onSuccess: async () => {
      setPaymentVoidReason('');
      await client.invalidateQueries({ queryKey: ['invoice', invoiceId] });
    },
  });

  const cancel = useMutation({
    mutationFn: () => voidInvoice(invoiceId, voidReason),
    onSuccess: async () => {
      setVoidReason('');
      await client.invalidateQueries({ queryKey: ['invoice', invoiceId] });
    },
  });

  const adjust = useMutation({
    mutationFn: () => adjustInvoice(invoiceId, {
      amount: direction === 'DEDUCTION' ? -Math.abs(Number(amount)) : Math.abs(Number(amount)),
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
        {view.overdue === true && (
          <>
            {' / '}
            {/* **列ではなくサーバが数える**（不変条件 4）。期限当日は超過ではない。 */}
            <span className="font-semibold text-red-700">未払い</span>
          </>
        )}
        {' / '}
        算出 {formatBusinessDateTime(view.calculatedAt)}
        {typeof view.dueOn === 'string' && (
          <>
            {' / '}
            支払期限 {view.dueOn}
          </>
        )}
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
                {line.reversed === true && (
                  <span className="ml-2 text-sm text-gray-600">取り消し済み</span>
                )}
                {/* 算出済のあいだだけ取り消せる。発行したあとは取り消しでは
                    なく請求書そのものを取り消す（不変条件 6）。 */}
                {view.status === 'CALCULATED'
                  && line.itemType === 'ADJUSTMENT'
                  && typeof line.adjustmentId === 'string'
                  && line.reversed !== true && (
                  <ReverseAdjustment invoiceId={invoiceId} line={line} />
                )}
                {line.basisExceptionId !== null && (
                  <>
                    {' '}
                    {/* **根拠へ飛べるようにする**（US28 §8 の受け側）。ID を出す
                        だけでは、探しに行くのは人の仕事になる。

                        **飛び先は根拠の種類で決まる。** 留置の保管料は通関申告
                        （`IMP-…`）が根拠で、例外一覧へ飛ばすと該当なしになる
                        （IT13 のレビュー 高）。 */}
                    <Link className={LINK} to={basisLinkOf(line.basisExceptionId)}>
                      {basisLabelOf(line.basisExceptionId)}（{line.basisExceptionId}）
                    </Link>
                  </>
                )}
              </td>
            </tr>
          ))}
          {/* **見積を経ない予約では出さない**（注 N12）。出すと「見積が無い」
              ことを「差額 0」と読み違える。 */}
          {typeof view.quotedAmount === 'number' && (
            <tr>
              <td className={TD}>見積時の概算</td>
              <td className={TD}>{formatMoney(view.quotedAmount, view.currency)}</td>
              <td className={TD}>見積の候補経路（実際に通った区間とは異なる）</td>
            </tr>
          )}
          <tr>
            <td className={TD}><b>合計</b></td>
            <td className={TD}><b>{formatMoney(view.totalAmount, view.currency)}</b></td>
            <td className={TD} />
          </tr>
          {typeof view.quotedAmount === 'number' && (
            <tr>
              <td className={TD}>差額</td>
              <td className={TD}>
                {/* **符号で向きを出す。** 概算より高いのか安いのかが、金額だけでは
                    読めない。 */}
                {view.totalAmount - view.quotedAmount >= 0 ? '+ ' : ''}
                {formatMoney(view.totalAmount - view.quotedAmount, view.currency)}
              </td>
              <td className={TD}>
                見積は候補経路、請求は実際に通った区間で数えます（区間数の増減・誤配・留置）
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {/* **算出済のあいだだけ発行できる**（US23 §受入基準 1）。発行すると額が
          確定するので、調整はもう受け付けない。 */}
      {view.status === 'CALCULATED' && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">請求書を発行する</h2>
          <p className="mt-1 text-sm text-gray-600">
            発行すると支払期限（発行日 + 30 日）が確定し、荷主が自社の請求書を読めるようになります。
            発行後は調整を入れられません。
          </p>
          <button
            className={`${BUTTON_PRIMARY} mt-3`}
            type="button"
            disabled={issue.isPending}
            onClick={() => issue.mutate()}
          >
            請求書を発行する
          </button>
          {issue.isPending && <output className={`${NOTICE} ml-3`}>送信中…</output>}
          {issue.isError && (
            <p role="alert" className={`${ALERT} mt-3`}>
              {issue.error instanceof ApiError
                ? issue.error.message : '発行できませんでした'}
            </p>
          )}
        </section>
      )}

      {/* **発行済のあいだだけ入金を記録できる**（US23 §受入基準 3・4）。
          発行していない請求書への入金は「何に対する入金か」が決まらない。 */}
      {view.status === 'INVOICED' && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">入金を記録する</h2>
          <p className="mt-1 text-sm text-gray-600">
            決済機関との連携はありません。入金明細を見て記録してください。
            入金日は<b>入金のあった日</b>です（記録した日ではありません）。
          </p>
          <form
            className="mt-3 flex flex-wrap items-end gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              pay.mutate(view.totalAmount);
            }}
          >
            <div>
              <label className={LABEL} htmlFor="payment-paid-at">入金日</label>
              <input
                id="payment-paid-at"
                className={FIELD}
                type="date"
                value={paidAt}
                onChange={(event) => setPaidAt(event.target.value)}
              />
            </div>
            <div>
              <span className={LABEL}>入金額</span>
              <p className="mt-1 text-sm text-gray-800">
                {formatMoney(view.totalAmount, view.currency)}（請求額）
              </p>
            </div>
            <button className={BUTTON_PRIMARY} type="submit" disabled={pay.isPending}>
              入金を記録する
            </button>
            {pay.isPending && <output className={NOTICE}>送信中…</output>}
          </form>
          {pay.isError && (
            <p role="alert" className={`${ALERT} mt-3`}>
              {pay.error instanceof ApiError ? pay.error.message : '入金を記録できませんでした'}
            </p>
          )}
        </section>
      )}

      {/* **入金の記録と、その取り消し**（IT15 引き継ぎ 3）。
          取り消した入金も出す——行を消さないのは「誤って記録して取り消した」
          事実を残すためで、出さなければ残した意味が無い。 */}
      {view.payments.length > 0 && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">入金</h2>
          <ul className="mt-3 space-y-3">
            {view.payments.map((payment) => (
              <li key={payment.paymentId} className="border-t border-gray-100 pt-3 first:border-0 first:pt-0">
                <p className="text-sm text-gray-800">
                  {formatMoney(payment.amount, payment.currency)}
                  <span className="ml-2 text-gray-600">{formatBusinessDateTime(payment.paidAt)} 入金</span>
                  {payment.recordedBy && <span className="ml-2 text-gray-500">記録: {payment.recordedBy}</span>}
                </p>
                {payment.voidedAt ? (
                  <p className="mt-1 text-sm text-gray-600">
                    <b>取消済</b>（{payment.voidedBy}）: {payment.voidReason}
                  </p>
                ) : (
                  <form
                    className="mt-2 flex flex-wrap items-end gap-3"
                    onSubmit={(event) => {
                      event.preventDefault();
                      revokePayment.mutate(payment.paymentId);
                    }}
                  >
                    <div className="grow">
                      <label className={LABEL} htmlFor="payment-void-reason">取消の理由</label>
                      <input
                        id="payment-void-reason"
                        className={FIELD}
                        value={paymentVoidReason}
                        onChange={(event) => setPaymentVoidReason(event.target.value)}
                        placeholder="他社の入金と取り違えた"
                      />
                    </div>
                    <button
                      className={BUTTON_SECONDARY}
                      type="submit"
                      disabled={revokePayment.isPending || paymentVoidReason.trim() === ''}
                    >
                      入金を取り消す
                    </button>
                    {revokePayment.isPending && <output className={NOTICE}>送信中…</output>}
                  </form>
                )}
              </li>
            ))}
          </ul>
          {revokePayment.isError && (
            <p role="alert" className={`${ALERT} mt-3`}>
              {revokePayment.error instanceof ApiError
                ? revokePayment.error.message
                : '入金を取り消せませんでした'}
            </p>
          )}
        </section>
      )}

      {/* **算出済と請求済は取り消せる。** 入金済は取り消さない——決着したものを
          動かすと、入金の事実と請求書の状態が食い違う。 */}
      {(view.status === 'CALCULATED' || view.status === 'INVOICED') && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">請求書を取り消す</h2>
          <p className="mt-1 text-sm text-gray-600">
            取り消した請求書は<b>再発行できません</b>。出し直すときは新しく作り直します。
          </p>
          <form
            className="mt-3 flex flex-wrap items-end gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              cancel.mutate();
            }}
          >
            <div className="grow">
              <label className={LABEL} htmlFor="void-reason">取消の理由</label>
              <input
                id="void-reason"
                className={FIELD}
                value={voidReason}
                onChange={(event) => setVoidReason(event.target.value)}
              />
            </div>
            <button className={BUTTON_DANGER} type="submit" disabled={cancel.isPending}>
              請求書を取り消す
            </button>
          </form>
          {cancel.isError && (
            <p role="alert" className={`${ALERT} mt-3`}>
              {cancel.error instanceof ApiError
                ? cancel.error.message : '取り消せませんでした'}
            </p>
          )}
        </section>
      )}

      {/* 算出済のあいだだけ調整を受け付ける（US21 §受入基準 6）。押せるのに
          断られる操作を並べない。 */}
      {view.status === 'CALCULATED' && (
        <section className={`${CARD} mt-4`}>
          <h2 className="text-base font-semibold text-gray-900">料金を調整する</h2>
          <p className="mt-1 text-sm text-gray-600">
            向きを選び、金額は正の数で入れます。理由は必須です。
          </p>
          <form
            className="mt-3 grid gap-4 sm:grid-cols-4"
            onSubmit={(event) => {
              event.preventDefault();
              adjust.mutate();
            }}
          >
            <div>
              <label className={LABEL} htmlFor="adjust-direction">調整の向き</label>
              <select
                id="adjust-direction"
                className={FIELD}
                value={direction}
                onChange={(event) =>
                  setDirection(event.target.value as 'DEDUCTION' | 'COMPENSATION')}
              >
                <option value="DEDUCTION">減額（請求を減らす）</option>
                <option value="COMPENSATION">補償費用（請求を増やす）</option>
              </select>
            </div>
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
            <div className="sm:col-span-4">
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

/**
 * 調整を取り消す（IT14 引き継ぎ C）。
 *
 * <p><b>理由を聞いてから送る。</b> 理由の読めない取り消しを残すと、あとから
 * 誰も何が起きたか確かめられない。</p>
 */
function ReverseAdjustment({
  invoiceId,
  line,
}: {
  readonly invoiceId: string;
  readonly line: InvoiceLineView;
}) {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const reverse = useMutation({
    mutationFn: () => reverseAdjustment(invoiceId, String(line.adjustmentId), reason),
    onSuccess: async () => {
      setOpen(false);
      setReason('');
      await client.invalidateQueries({ queryKey: ['invoice', invoiceId] });
    },
  });

  if (!open) {
    return (
      <button type="button" className={`${LINK} ml-2`} onClick={() => setOpen(true)}>
        この調整を取り消す
      </button>
    );
  }

  return (
    <div className="mt-2">
      <label className={LABEL} htmlFor={`reverse-${line.adjustmentId}`}>取り消しの理由</label>
      <input
        id={`reverse-${line.adjustmentId}`}
        className={FIELD}
        value={reason}
        onChange={(event) => setReason(event.target.value)}
      />
      <button
        type="button"
        className={`${BUTTON_PRIMARY} mt-2`}
        disabled={reverse.isPending}
        onClick={() => reverse.mutate()}
      >
        取り消しを確定する
      </button>
      {reverse.isError && (
        <p role="alert" className={`${ALERT} mt-2`}>
          {reverse.error instanceof ApiError
            ? reverse.error.message : '取り消せませんでした'}
        </p>
      )}
    </div>
  );
}
