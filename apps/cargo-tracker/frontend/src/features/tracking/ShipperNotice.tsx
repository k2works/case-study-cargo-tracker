import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useAuthStore } from '@/shared/auth/authStore';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { LINK } from '@/shared/ui/styles';
import { fetchNotices, markNoticesRead } from './api';

/** 知らせを見に行く間隔（ADR-0021。押し出さずに読みに行く）。 */
const REFETCH_INTERVAL_MS = 60_000;

/**
 * 貨物の知らせ（US37 §受入基準 1・2・3・5）。
 *
 * <p><b>画面ではなく共通レイアウトの要素である</b>（注 N5）。荷主が
 * ログインしているあいだ、どの画面にいても出る——知らせは「いま見ている画面」
 * とは無関係に起きる。</p>
 *
 * <p><b>荷主以外は問い合わせにも行かない</b>（§5）。「返ってきたものが空だから
 * 出さない」にすると、他のロールの画面から 403 が出続け、記録が荒れる。</p>
 *
 * <p><b>既読はサーバに送る</b>（§3）。ブラウザに持つと、荷主が端末を使い分けた
 * とき同じ知らせが行く先々でもう一度出る。</p>
 *
 * <p><b>行き先は 2 つある</b>——貨物の詳細（S41）と、予約の進み具合（S46）。
 * 知らせは追跡番号しか持たないので、予約へは S41 を経由する。</p>
 */
export function ShipperNotice() {
  const isShipper = useAuthStore((state) => state.user?.roles.includes('ROLE_SHIPPER') ?? false);

  // **荷主以外は問い合わせにも行かない**（§5）。`enabled` で止めるのではなく
  // <b>問い合わせる部品そのものを描かない</b>——フックは条件で飛ばせないので、
  // 「読みに行く側」を別の部品に分けるのが唯一の確実な形である。
  return isShipper ? <ShipperNoticePopup /> : null;
}

function ShipperNoticePopup() {
  const client = useQueryClient();

  const notices = useQuery({
    queryKey: ['shipper-notices'],
    queryFn: fetchNotices,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const dismiss = useMutation({
    mutationFn: (sequenceNo: number) => markNoticesRead(sequenceNo),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['shipper-notices'] });
    },
  });

  const ready = notices.data?.state === 'ready' ? notices.data.value : null;
  if (ready === null || ready.items.length === 0) {
    return null;
  }

  return (
    <aside
      aria-label="貨物の知らせ"
      className="fixed bottom-4 right-4 z-50 w-80 rounded border border-blue-200 bg-white
        p-4 shadow-lg"
    >
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-sm font-semibold text-gray-900">
          貨物の知らせ（{ready.items.length} 件）
        </h2>
        <button
          type="button"
          className={LINK}
          onClick={() => dismiss.mutate(ready.latestSequence)}
        >
          閉じる
        </button>
      </div>
      <ul className="mt-2 space-y-2 text-sm text-gray-700">
        {ready.items.map((notice) => (
          <li key={notice.sequenceNo}>
            <Link className={LINK} to={`/tracking/${notice.trackingNumber}`}>
              {notice.trackingNumber}
            </Link>
            <span className="ml-1">
              {notice.statusLabel}
              {notice.location !== null && `（${notice.location}）`}
            </span>
            <div className="text-xs text-gray-500">
              {notice.originUnLocode} → {notice.destinationUnLocode}
              {' / '}
              {formatBusinessDateTime(notice.occurredAt)}
            </div>
          </li>
        ))}
      </ul>
      {/* **知らせから予約の進み具合へも行ける。** 追跡番号しか持たないので、
          一覧（S45）経由で辿る——行き先を当てずっぽうで組み立てない。 */}
      <p className="mt-3 text-sm">
        <Link className={LINK} to="/shipper/bookings">
          自社の予約一覧へ
        </Link>
      </p>
    </aside>
  );
}
