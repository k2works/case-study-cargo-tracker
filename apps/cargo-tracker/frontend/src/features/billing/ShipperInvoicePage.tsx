import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { formatMoney } from '@/shared/ui/money';
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
import { fetchShipperInvoice, fetchShipperInvoiceOfBooking } from './api';

/**
 * S62 自社請求書（荷主 / US23 §受入基準 2）。
 *
 * <p><b>金額を出す唯一の荷主向け画面である。</b> 他の荷主向け画面（S45・S46）は
 * 金額を出さない。</p>
 *
 * <p><b>経理向けの S60・S61 とは別の経路を叩く</b>（`/billing/shipper-invoices`）。
 * 同じ経路にロールで分岐を足すと、載せ忘れた分岐ほど無防備になる。荷主 ID は
 * Gateway が JWT から取り出して伝え、サーバで突き合わせる。</p>
 *
 * <p><b>社内の例外 ID へのリンクは出さない。</b> 荷主には開く先が無く、出しても
 * 行き止まりになる。金額の根拠（明細の説明文）だけで足りる。</p>
 *
 * <p><b>他社の請求書は「ありません」として扱う。</b> 「権限がありません」と出すと
 * その請求書が存在することを教えてしまう（サーバも 404 を返す）。</p>
 */
export function ShipperInvoicePage() {
  // **2 つの入口がある。** 請求書番号で開く（正典の経路）のと、予約番号で開く
  // （追跡詳細からの導線）。荷主は請求書番号を知らないので、後者が実際に使われる。
  const { invoiceId, bookingId } = useParams();
  const invoice = useQuery({
    queryKey: ['shipper-invoice', invoiceId ?? '', bookingId ?? ''],
    queryFn: () => (bookingId === undefined
      ? fetchShipperInvoice(String(invoiceId))
      : fetchShipperInvoiceOfBooking(bookingId)),
    retry: false,
  });

  if (invoice.isError) {
    return (
      <p role="alert" className={ALERT}>
        請求書が見つかりません。請求書番号をお確かめください。
      </p>
    );
  }
  if (invoice.data?.state === 'pending') {
    return <output className={`${NOTICE} block`}>{invoice.data.message}</output>;
  }
  if (invoice.data?.state !== 'ready') {
    return <output className={`${NOTICE} block`}>読み込み中です</output>;
  }

  const view = invoice.data.value;

  return (
    <section>
      <h1 className={PAGE_TITLE}>請求書 {view.invoiceId}</h1>
      <p className="mt-1 text-sm text-gray-600">
        予約 {view.bookingId}
        {' / '}
        <span>{view.statusLabel}</span>
        {typeof view.dueOn === 'string' && (
          <>
            {' / '}
            支払期限 {view.dueOn}
          </>
        )}
      </p>

      <p className="mt-3 text-lg font-semibold text-gray-900">
        ご請求額 {formatMoney(view.totalAmount, view.currency)}
      </p>

      <div className={`${CARD} mt-4 overflow-x-auto`}>
        <table className={TABLE}>
          <caption className={TABLE_CAPTION}>明細</caption>
          <thead>
            <tr>
              <th scope="col" className={TH}>項目</th>
              <th scope="col" className={TH}>金額</th>
            </tr>
          </thead>
          <tbody>
            {view.lineItems.map((line, index) => (
              <tr key={`${line.itemType}-${index}`}>
                {/* **根拠の例外 ID は出さない。** 荷主には開く先が無い。 */}
                <td className={TD}>{line.description}</td>
                <td className={TD}>
                  {formatMoney(
                    line.itemType === 'DISCOUNT' ? -line.amount : line.amount, line.currency)}
                </td>
              </tr>
            ))}
            <tr>
              <td className={TD}><b>合計</b></td>
              <td className={TD}><b>{formatMoney(view.totalAmount, view.currency)}</b></td>
            </tr>
          </tbody>
        </table>
      </div>

      {typeof view.issuedOn === 'string' && (
        <p className="mt-3 text-sm text-gray-600">発行 {view.issuedOn}</p>
      )}

      {/* **開いた先から戻れるようにする。** 戻れないとブラウザの戻るに頼ることに
          なる（共有画面のリンクもロールで出し分ける・IT7 の教訓）。 */}
      <p className="mt-4">
        <Link className={LINK} to={`/shipper/bookings/${view.bookingId}`}>
          自社予約の進み具合へ戻る
        </Link>
      </p>
    </section>
  );
}
