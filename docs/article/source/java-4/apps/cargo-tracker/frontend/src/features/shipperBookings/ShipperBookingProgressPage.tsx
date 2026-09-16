import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { bookingStatusLabel, routingStatusLabel } from '@/features/bookings/api';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
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
import { fetchShipperBookingProgress } from './api';

/** 進み具合として並べる段。<b>順序が業務の意味を持つ</b>（仮受付から配送完了へ）。 */
const STEPS: readonly { readonly label: string; readonly at: (
  view: { readonly bookedAt: string; readonly routingRequestedAt: string | null;
    readonly lastNotifiedAt: string | null; readonly confirmedAt: string | null;
    readonly trackingIssuedAt: string | null },
) => string | null }[] = [
  { label: '仮受付', at: (view) => view.bookedAt },
  { label: '経路設計へ引き渡し', at: (view) => view.routingRequestedAt },
  { label: '経路をご案内', at: (view) => view.lastNotifiedAt },
  { label: '予約確定', at: (view) => view.confirmedAt },
  { label: '追跡番号発行', at: (view) => view.trackingIssuedAt },
];

/**
 * S46 自社予約の進み具合（荷主 / UC10・UC15）。
 *
 * <p><b>金額・社内メモ・担当者名は出さない</b>（ui_design.md「S46」）。サーバも
 * 渡さない。出すのは状態・進み具合・確定旅程・連絡の記録だけである。</p>
 *
 * <p><b>他社の予約は「ありません」として扱う。</b> 「権限がありません」と出すと
 * その予約が存在することを教えてしまう（サーバも 404 を返す）。</p>
 *
 * <p><b>次に行ける先を出す。</b> 追跡番号が出ていれば S41 へ、請求後は S62 へ。
 * 気づく手段は、その人が次に取れる行動へ繋がらなければ仕事が進まない。</p>
 */
export function ShipperBookingProgressPage() {
  const { bookingId } = useParams();
  const progress = useQuery({
    queryKey: ['shipper-booking-progress', bookingId ?? ''],
    queryFn: () => fetchShipperBookingProgress(String(bookingId)),
    retry: false,
  });

  if (progress.isError) {
    return (
      <p role="alert" className={ALERT}>
        予約が見つかりません。予約番号をお確かめください。
      </p>
    );
  }
  if (progress.data?.state === 'pending') {
    return <output className={`${NOTICE} block`}>{progress.data.message}</output>;
  }
  if (progress.data?.state !== 'ready') {
    return <output className={`${NOTICE} block`}>読み込み中です</output>;
  }

  const view = progress.data.value;

  return (
    <section>
      <h1 className={PAGE_TITLE}>予約 {view.bookingNumber}</h1>
      <p className="mt-1 text-sm text-gray-600">
        {view.originUnLocode} → {view.destinationUnLocode}
        {' / '}
        到着期限 {view.arrivalDeadline}
        {' / '}
        {bookingStatusLabel(view.bookingStatus)}
        {' / 経路 '}
        {routingStatusLabel(view.routingStatus)}
      </p>

      <div className={`${CARD} mt-4`}>
        <h2 className="text-sm font-semibold text-gray-900">進み具合</h2>
        <ol className="mt-2 space-y-1 text-sm text-gray-700">
          {STEPS.map((step) => {
            const at = step.at(view);
            return (
              <li key={step.label}>
                <span aria-hidden="true">{at === null ? '○' : '●'}</span>{' '}
                {step.label}
                {at !== null && ` ${formatBusinessDateTime(at)}`}
                {at === null && '（これから）'}
              </li>
            );
          })}
        </ol>
      </div>

      <div className={`${CARD} mt-4 overflow-x-auto`}>
        {view.legs.length === 0 ? (
          <p className="text-sm text-gray-600">
            確定した旅程はまだありません。経路が決まりましたらご案内します。
          </p>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>確定旅程</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>区間</th>
                <th scope="col" className={TH}>航海</th>
                <th scope="col" className={TH}>積込</th>
                <th scope="col" className={TH}>荷降し</th>
              </tr>
            </thead>
            <tbody>
              {view.legs.map((leg) => (
                <tr key={leg.legSeq}>
                  <td className={TD}>{leg.legSeq}</td>
                  <td className={TD}>{leg.voyageNumber}</td>
                  <td className={TD}>
                    {leg.loadUnLocode}
                    {leg.loadAt !== null && ` ${formatBusinessDateTime(leg.loadAt)}`}
                  </td>
                  <td className={TD}>
                    {leg.unloadUnLocode}
                    {leg.unloadAt !== null && ` ${formatBusinessDateTime(leg.unloadAt)}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className={`${CARD} mt-4 overflow-x-auto`}>
        {view.notifications.length === 0 ? (
          <p className="text-sm text-gray-600">ご連絡の記録はまだありません。</p>
        ) : (
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>ご連絡の記録</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>日時</th>
                <th scope="col" className={TH}>内容</th>
              </tr>
            </thead>
            <tbody>
              {view.notifications.map((notification) => (
                <tr key={notification.notifiedAt}>
                  <td className={TD}>{formatBusinessDateTime(notification.notifiedAt)}</td>
                  <td className={TD}>{notification.summary}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <p className="mt-4 space-x-4 text-sm">
        <Link to="/shipper/bookings" className={LINK}>予約一覧へ</Link>
        {view.trackingNumber !== null && (
          <Link to={`/tracking/${view.trackingNumber}`} className={LINK}>追跡を見る</Link>
        )}
        <Link to={`/shipper/invoices/by-booking/${view.bookingId}`} className={LINK}>
          請求書を見る
        </Link>
      </p>
    </section>
  );
}
