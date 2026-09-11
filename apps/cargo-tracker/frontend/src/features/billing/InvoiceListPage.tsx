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
 * <p><b>並べ直さない。</b> 算出日時の新しい順をサーバが決める。正典の一覧規約は
 * 「支払期限が近い順」だが、<b>期限が決まるのは発行のとき</b>（US23・IT14）で、
 * いまは入っていない列で並べても順序が決まらない。期限が入る IT14 で切り替える。</p>
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
  const invoices = useQuery({
    queryKey: ['invoices', includeSettled, bookingId],
    queryFn: () => fetchInvoices(includeSettled, bookingId),
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = invoices.data?.state === 'ready' ? invoices.data.value.items : [];

  return (
    <div>
      <h1 className={PAGE_TITLE}>請求一覧</h1>
      <p className="mt-1 text-sm text-gray-600">
        {bookingId !== null && <>予約 {bookingId} の請求書だけを出しています。</>}
        算出が新しいものから並びます。
        {includeSettled ? '入金済・取消のものも出ています。' : '入金済・取消のものは出ません。'}
      </p>

      <section className={`${CARD} mt-4`}>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={includeSettled}
            onChange={(event) => setIncludeSettled(event.target.checked)}
          />
          <span>入金済も表示</span>
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
              <td className={TD}>{formatMoney(invoice.totalAmount, invoice.currency)}</td>
            </tr>
          ))}
          {items.length === 0 && invoices.data?.state === 'ready' && (
            <tr>
              <td className={TD} colSpan={6}>
                算出済の請求はありません。引取が完了すると自動で作られます。
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
