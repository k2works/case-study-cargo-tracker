import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import {
  ALERT,
  CARD,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { fetchInvoices, formatMoney } from './api';

/** 一覧は 30 秒ごとに更新する（ui_design.md「ポーリング」）。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * S60 請求一覧（UC17 / US21）。
 *
 * <p><b>既定で入金済・取消を外す。</b> 決着したものが混ざると、一覧全体が
 * 「まだ手を入れる場所」に見えなくなる。読み口が外して返す。</p>
 *
 * <p><b>並べ直さない。</b> 並びはサーバが決める——通常は算出日時の新しい順、
 * 未払いだけに絞ったときは<b>支払期限の近い順</b>（正典の一覧規約）。全件を
 * 出したまま画面で並べ直すと、上限の打ち切りで期限の近いものが漏れる。</p>
 *
 * <p><b>未払いだけに絞れるようにする</b>（US23 §受入基準 5）。督促は「期限を
 * 過ぎたもの」から始まる——一覧全体から目で探させると、件数が増えるほど
 * 取りこぼす。<b>数えるのはサーバ</b>で、期限当日は未払いにしない。</p>
 */
export function InvoiceListPage() {
  const [searchParams] = useSearchParams();
  // **絞り込んで来た人は、その絞り込みの一覧を見たい。** 予約詳細（S22）からは
  // `?bookingId=` で来る。読まないと全件が出て、どれがその予約の請求書かを
  // もう一度自分で探すことになる（気づく手段が次の行動へ繋がらない）。
  const bookingId = searchParams.get('bookingId');
  // 予約を指して来たときは決着したものも見せる——「済んでいる」ことが
  // 知りたくて来ている。
  const [includeSettled, setIncludeSettled] = useState(bookingId !== null);
  /** 未払いだけに絞る。**督促はここから始まる**（US23 §受入基準 5）。 */
  // ダッシュボードの知らせからは `?overdue=true` で来る。読まないと全件が出て、
  // どれが未払いかをもう一度自分で探すことになる。
  const [overdueOnly, setOverdueOnly] = useState(searchParams.get('overdue') === 'true');
  const invoices = useQuery({
    queryKey: ['invoices', includeSettled, bookingId, overdueOnly],
    queryFn: () => fetchInvoices(includeSettled, bookingId, overdueOnly),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = invoices.data?.state === 'ready' ? invoices.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>請求一覧</h1>
      <p className="mt-1 text-sm text-gray-600">
        {bookingId !== null && <>予約 {bookingId} の請求書だけを出しています。</>}
        {overdueOnly
          ? '支払期限を過ぎたものだけを、期限の近いものから出しています。'
          : '算出が新しいものから並びます。'}
        {!overdueOnly
          && (includeSettled
            ? '入金済・取消のものも出ています。'
            : '入金済・取消のものは出ません。')}
      </p>

      <section className={`${CARD} mt-4`}>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={includeSettled}
            onChange={(event) => setIncludeSettled(event.target.checked)}
          />
          {/* **取消も一緒に出る。** ラベルが「入金済」だけだと、取り消された
              請求書が混ざった理由が読めない（IT13 のレビュー 中）。 */}
          <span>入金済・取消も表示</span>
        </label>
        <label className="mt-2 flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={overdueOnly}
            onChange={(event) => setOverdueOnly(event.target.checked)}
          />
          {/* **期限当日は未払いにならない。** 当日中の入金はふつうにある。 */}
          <span>未払い（支払期限を過ぎたもの）だけ表示</span>
        </label>
      </section>

      {invoices.data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4 block`}>{invoices.data.message}</output>
      )}
      {invoices.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>一覧を取得できませんでした</p>
      )}

      <table className={`${TABLE} mt-4`}>
        <caption className={TABLE_CAPTION}>請求一覧（{items.length} 件）</caption>
        <thead>
          <tr>
            <th className={TH} scope="col">算出</th>
            <th className={TH} scope="col">請求書</th>
            <th className={TH} scope="col">予約</th>
            <th className={TH} scope="col">荷主</th>
            <th className={TH} scope="col">状態</th>
            {/* **期限を出す。** 状態だけだと、あと何日あるのかが読めない。 */}
            <th className={TH} scope="col">支払期限</th>
            <th className={TH} scope="col">合計</th>
          </tr>
        </thead>
        <tbody>
          {items.map((invoice) => (
            <tr key={invoice.invoiceId}>
              <td className={TD}>{formatBusinessDateTime(invoice.calculatedAt)}</td>
              <td className={TD}>
                <Link className={LINK} to={`/invoices/${invoice.invoiceId}`}>
                  {invoice.invoiceId}
                </Link>
              </td>
              <td className={TD}>{invoice.bookingId}</td>
              <td className={TD}>
                {/* **鍵を破棄した荷主は名前が消える**（ADR-0003）。空欄にすると
                    「取得できていない」と読まれるので、そう書く。 */}
                {invoice.shipperName ?? '（削除済）'}（{invoice.shipperTypeLabel}）
              </td>
              <td className={TD}>{invoice.statusLabel}</td>
              <td className={TD}>
                {/* 未発行では期限が無い。空欄にすると「取得できていない」と
                    読まれるので、そう書く。 */}
                {invoice.dueOn ?? '未発行'}
                {invoice.overdue === true && (
                  <span className="ml-2 font-semibold text-red-700">未払い</span>
                )}
              </td>
              <td className={TD}>{formatMoney(invoice.totalAmount, invoice.currency)}</td>
            </tr>
          ))}
          {items.length === 0 && invoices.data?.state === 'ready' && (
            <tr>
              <td className={TD} colSpan={7}>
                {overdueOnly
                  ? '支払期限を過ぎた請求書はありません。'
                  : '算出済の請求はありません。引取が完了すると自動で作られます。'}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
