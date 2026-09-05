import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  LINK,
  NOTICE,
  PAGE_TITLE,
  SECTION_TITLE,
} from '@/shared/ui/styles';
import { ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import { canRequestRouting, canUpdateSpecification } from './transitions';
import { requestRouting } from '@/features/routing/api';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { display } from '@/features/shippers/api';
import { bookingStatusLabel, cargoTypeLabel, fetchBooking } from './api';

/**
 * S22 予約詳細（UC04）。
 *
 * <p>IT2 の範囲は状態・貨物仕様・輸送条件まで。旅程・通知履歴・誤配バナーは
 * それを作るイテレーションで足す。中身の無い欄を先に置くと、動くと誤解される。</p>
 */
export function BookingDetailPage() {
  const { bookingId = '' } = useParams();
  const queries = useQueryClient();
  // 引き渡すのは営業の仕事（US06）。詳細画面は経路設計・追跡にも開いているので、
  // 状態だけで出し分けると、見に来ただけの人が引き渡せる。
  // これは表示の話で、守りは Gateway の認可（ADR-0006）が担う。
  const isSales = useAuthStore((state) => state.user?.roles.includes('ROLE_SALES') ?? false);
  const { data, isPending, isError } = useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => fetchBooking(bookingId),
    // 登録直後は投影がまだなので 202 が返る。反映されるまで取り直す。
    refetchInterval: (query) => (query.state.data?.state === 'pending' ? 2000 : false),
  });

  const handOver = useMutation({
    mutationFn: () => requestRouting(bookingId),
    onSuccess: () => queries.invalidateQueries({ queryKey: ['booking', bookingId] }),
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>
        {data?.state === 'ready' ? `予約 ${data.value.bookingNumber}` : '予約'}
      </h1>
      <p className="mt-2 text-sm">
        <Link to="/bookings" className={LINK}>
          予約一覧に戻る
        </Link>
      </p>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          予約を取得できませんでした
        </p>
      )}
      {/* 受け付けたことと反映が終わったことは別。404 にすると「登録に失敗した」
          と読めてしまう。 */}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {data?.state === 'ready' && (
        <div className={`${CARD} mt-4 space-y-6`}>
          <div>
            <h2 className={SECTION_TITLE}>状態</h2>
            <dl className="mt-2 grid gap-2 sm:grid-cols-2">
              <Row label="予約の状態" value={bookingStatusLabel(data.value.bookingStatus)} />
              <Row label="荷主" value={display(data.value.shipperName)} />
              {/* 一度も直していない予約に最終更新を出すと、受付日時と
                  区別が付かない。直したことのある予約だけに出す（US32）。 */}
              {data.value.updatedAt && (
                <Row
                  label="最終更新"
                  value={`${formatBusinessDateTime(data.value.updatedAt)}${
                    data.value.updatedBy ? `（${data.value.updatedBy}）` : ''
                  }`}
                />
              )}
            </dl>
          </div>

          <div>
            <h2 className={SECTION_TITLE}>輸送条件</h2>
            <dl className="mt-2 grid gap-2 sm:grid-cols-2">
              <Row label="出発地" value={data.value.originUnLocode} />
              <Row label="目的地" value={data.value.destinationUnLocode} />
              <Row label="到着期限" value={data.value.arrivalDeadline} />
            </dl>
          </div>

          {/* ボタンの出し分けは状態の述語をそのまま呼ぶ。ここで
              status === 'PRELIMINARY' と書くと、集約の遷移表と判断が二重になり、
              片方だけ直したときに食い違う。 */}
          {/* 修正できるのは仮受付だけ（US32）。判定は集約と同じ述語を呼ぶ。
              営業以外に出すと、押してから Gateway の 403 で気づくことになる。 */}
          {isSales && canUpdateSpecification(data.value.bookingStatus) && (
            <p className="text-sm">
              <Link to={`/bookings/${encodeURIComponent(bookingId)}/edit`} className={LINK}>
                修正する
              </Link>
            </p>
          )}

          {isSales && canRequestRouting(data.value.bookingStatus) && (
            <div>
              <button
                type="button"
                className={BUTTON_PRIMARY}
                disabled={handOver.isPending}
                onClick={() => handOver.mutate()}
              >
                {handOver.isPending ? '送信中…' : '経路設計を依頼する'}
              </button>
              {handOver.error instanceof ApiError && (
                <p role="alert" className={`${ALERT} mt-2`}>
                  {handOver.error.body.message}
                </p>
              )}
              {/* 通信断のように応答が返らない場合も黙らない。押しても何も
                  起きないように見えると、利用者は同じ操作を繰り返す。 */}
              {handOver.error !== null && !(handOver.error instanceof ApiError) && (
                <p role="alert" className={`${ALERT} mt-2`}>
                  引き渡せませんでした。通信の状態を確かめて、もう一度お試しください
                </p>
              )}
            </div>
          )}

          <div>
            <h2 className={SECTION_TITLE}>貨物</h2>
            <dl className="mt-2 grid gap-2 sm:grid-cols-2">
              <Row label="品名" value={data.value.productName} />
              <Row label="種別" value={cargoTypeLabel(data.value.cargoType)} />
              <Row label="重量" value={`${data.value.weightKg} kg`} />
              <Row label="数量" value={String(data.value.quantity)} />
              <Row
                label="寸法"
                value={
                  data.value.lengthCm
                    ? `${data.value.lengthCm} × ${data.value.widthCm} × ${data.value.heightCm} cm`
                    : '—'
                }
              />
              {data.value.hazardImoClass && (
                <Row
                  label="危険物申告"
                  value={`IMO ${data.value.hazardImoClass} / ${data.value.hazardUnNumber}`}
                />
              )}
              {data.value.temperatureMinC && (
                <Row
                  label="温度条件"
                  value={`${data.value.temperatureMinC} 〜 ${data.value.temperatureMaxC} ℃`}
                />
              )}
            </dl>
          </div>
        </div>
      )}
    </section>
  );
}

function Row({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="w-32 shrink-0 text-sm text-gray-600">{label}</dt>
      <dd className="text-sm text-gray-900">{value}</dd>
    </div>
  );
}
