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
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import { canRequestRouting, canUpdateSpecification } from './transitions';
import { requestRouting } from '@/features/routing/api';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { display } from '@/features/shippers/api';
import {
  bookingStatusLabel,
  cargoTypeLabel,
  fetchBooking,
  fetchBookingItinerary,
  fetchBookingRevisions,
  routingStatusLabel,
} from './api';

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
    //
    // **経路を確定した直後もここを通る。** 確定してから S22 へ来ると、投影が
    // 追いつくまで routingStatus は ROUTING_REQUESTED のままで、旅程の欄が
    // 現れない。確定を待っている間だけ取り直す（IT5 レビュー 高 1）。
    refetchInterval: (query) => {
      const state = query.state.data;
      if (state?.state === 'pending') {
        return 2000;
      }
      return state?.state === 'ready' && state.value.routingStatus === 'ROUTING_REQUESTED'
        ? 3000
        : false;
    },
  });

  // 修正履歴（US32 §受入基準 4）。一度も直していない予約では問い合わせない。
  // 「修正した」とだけ残っていて中身が読めない状態を作らないための読み口。
  // `!== null` にしない。項目が欠けた応答では undefined になり、「一度も直して
  // いない予約」が「直した予約」として扱われる（マニュアルのキャプチャで実測）。
  const updated = data?.state === 'ready' && Boolean(data.value.updatedAt);
  const revisions = useQuery({
    queryKey: ['booking', bookingId, 'revisions'],
    queryFn: () => fetchBookingRevisions(bookingId),
    enabled: updated,
  });

  // 旅程（US09）。経路が決まっていなければ問い合わせない。
  // 記録（cargo_leg）だけあって読み口が無いと、誰も区間を確かめられない。
  const routed = data?.state === 'ready' && data.value.routingStatus === 'ROUTED';
  const itinerary = useQuery({
    queryKey: ['booking', bookingId, 'itinerary'],
    queryFn: () => fetchBookingItinerary(bookingId),
    enabled: routed,
    // 経路を確定した直後は投影が数秒遅れる。1 回で諦めると、旅程の欄ごと
    // 現れないまま「失敗した」と読まれる（IT5 レビュー 高 1）。
    refetchInterval: (query) =>
      query.state.data?.state === 'ready' && query.state.data.value.legs.length > 0
        ? false
        : 2000,
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
              {/* 経路設定状態は予約の状態と別の軸。出さないと、この予約の経路が
                  いまどこまで進んだのかを予約詳細から読めない。 */}
              <Row
                label="経路設定状態"
                value={routingStatusLabel(data.value.routingStatus)}
              />
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

          {/* 経路が決まっているのに旅程が読めないことを黙らない。黙ると
              「まだ決まっていない予約」と同じ見た目になる（IT5 レビュー 中 7）。 */}
          {routed && itinerary.isError && (
            <p role="alert" className={ALERT}>
              旅程を取得できませんでした
            </p>
          )}
          {routed
            && !itinerary.isError
            && (itinerary.isPending
              || (itinerary.data?.state === 'ready'
                && itinerary.data.value.legs.length === 0)) && (
            <output className={NOTICE}>経路の反映を待っています</output>
          )}

          {routed
            && itinerary.data?.state === 'ready'
            && itinerary.data.value.legs.length > 0 && (
            <div>
              <h2 className={SECTION_TITLE}>旅程</h2>
              <div className="mt-2 overflow-x-auto">
                <table className={TABLE}>
                  <caption className={TABLE_CAPTION}>積む順に並んでいます</caption>
                  <thead>
                    <tr>
                      <th scope="col" className={TH}>区間</th>
                      <th scope="col" className={TH}>航海</th>
                      <th scope="col" className={TH}>積地 → 揚地</th>
                      <th scope="col" className={TH}>積込</th>
                      <th scope="col" className={TH}>荷揚</th>
                    </tr>
                  </thead>
                  <tbody>
                    {itinerary.data.value.legs.map((leg) => (
                      <tr key={leg.legSeq} data-testid={`leg-${leg.legSeq}`}>
                        <td className={TD}>{leg.legSeq}</td>
                        <td className={TD}>{leg.voyageNumber}</td>
                        <td className={TD}>
                          {leg.loadUnLocode} → {leg.unloadUnLocode}
                        </td>
                        <td className={TD}>{formatBusinessDateTime(leg.loadAt)}</td>
                        <td className={TD}>{formatBusinessDateTime(leg.unloadAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {updated
            && revisions.data?.state === 'ready'
            && revisions.data.value.items.length > 0 && (
            <div>
              <h2 className={SECTION_TITLE}>修正履歴</h2>
              <div className="mt-2 overflow-x-auto">
                <table className={TABLE}>
                  <caption className={TABLE_CAPTION}>新しい修正が先に並んでいます</caption>
                  <thead>
                    <tr>
                      <th scope="col" className={TH}>いつ</th>
                      <th scope="col" className={TH}>誰が</th>
                      <th scope="col" className={TH}>項目</th>
                      <th scope="col" className={TH}>変更前</th>
                      <th scope="col" className={TH}>変更後</th>
                    </tr>
                  </thead>
                  <tbody>
                    {revisions.data.value.items.map((item) => (
                      <tr
                        key={`${item.updatedAt}-${item.label}`}
                        data-testid={`revision-${item.label}`}
                      >
                        <td className={TD}>{formatBusinessDateTime(item.updatedAt)}</td>
                        {/* display() は鍵破棄で読めないことを表す（荷主名）。
                            修正した利用者が分からないのは別の意味なので使わない。 */}
                        <td className={TD}>{item.updatedBy ?? '—'}</td>
                        <td className={TD}>{item.label}</td>
                        <td className={TD}>{item.before}</td>
                        <td className={TD}>{item.after}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
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
