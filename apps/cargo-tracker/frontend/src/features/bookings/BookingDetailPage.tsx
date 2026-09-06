import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
  BUTTON_SECONDARY,
  CARD,
  FIELD,
  LABEL,
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
import {
  canIssueTrackingNumber,
  canNotifyShipper,
  canTransitionTo,
  canRequestRouting,
  canReturnToRouting,
  canUpdateSpecification,
} from './transitions';
import { requestRouting } from '@/features/routing/api';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { display } from '@/features/shippers/api';
import {
  bookingStatusLabel,
  cargoTypeLabel,
  confirmBooking,
  issueTrackingNumber,
  fetchBooking,
  fetchBookingItinerary,
  fetchBookingNotifications,
  fetchBookingRevisions,
  notifyShipper,
  returnToRouting,
  routingStatusLabel,
} from './api';
import type { ItineraryLegView } from './api';

/**
 * S22 予約詳細（UC04）。
 *
 * <p>IT2 の範囲は状態・貨物仕様・輸送条件まで。旅程・通知履歴・誤配バナーは
 * それを作るイテレーションで足す。中身の無い欄を先に置くと、動くと誤解される。</p>
 */
export function BookingDetailPage() {
  const { bookingId = '' } = useParams();
  // 通知を記録したあと、投影に届くまで取り直す合図。
  const [awaitingNotificationProjection, setAwaitingNotificationProjection] = useState(false);
  const queries = useQueryClient();
  // 引き渡すのは営業の仕事（US06）。詳細画面は経路設計・追跡にも開いているので、
  // 状態だけで出し分けると、見に来ただけの人が引き渡せる。
  // これは表示の話で、守りは Gateway の認可（ADR-0006）が担う。
  const isSales = useAuthStore((state) => state.user?.roles.includes('ROLE_SALES') ?? false);
  // 追跡番号の発行は経路設計者の操作（ui_design.md S22）。
  const isRouting = useAuthStore(
    (state) => state.user?.roles.includes('ROLE_ROUTING') ?? false);
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
      // 通知を記録した直後も投影が数秒遅れる。旅程と同じ形で待つ（IT6 レビュー 中）。
      // 待たないと「通知した記録を残す」を押しても状態も履歴も変わらず、
      // 利用者は同じ操作を繰り返す。
      if (state?.state === 'ready' && state.value.routingStatus === 'ROUTING_REQUESTED') {
        return 3000;
      }
      return state?.state === 'ready' && awaitingNotificationProjection ? 2000 : false;
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

  // 旅程（US09）。引き渡していない予約には無いので問い合わせない。
  // 記録（cargo_leg）だけあって読み口が無いと、誰も区間を確かめられない。
  //
  // **状態が ROUTED のときだけ問い合わせる形にしない。** 条件を調整したり
  // 経路設計へ戻したりすると設計依頼中に戻るが、確定済みの旅程は残っている
  // （ADR-0009 決定 3・US12）。状態で出し分けると、戻した瞬間に旅程が消えて
  // 「何を組み直すのか」が分からなくなる（クラスタの E2E で実測）。
  const routed = data?.state === 'ready' && data.value.routingStatus === 'ROUTED';
  const everRouted = data?.state === 'ready' && data.value.routingStatus !== 'NOT_ROUTED';
  const itinerary = useQuery({
    queryKey: ['booking', bookingId, 'itinerary'],
    queryFn: () => fetchBookingItinerary(bookingId),
    enabled: everRouted,
    // 経路を確定した直後は投影が数秒遅れる。1 回で諦めると、旅程の欄ごと
    // 現れないまま「失敗した」と読まれる（IT5 レビュー 高 1）。
    refetchInterval: (query) =>
      query.state.data?.state === 'ready' && query.state.data.value.legs.length > 0
        ? false
        : 2000,
  });

  // 通知履歴（US12 §受入基準 4）。**読むのは全員**——経路設計者も追跡も
  // 「荷主に何を伝えたか」を知る必要がある。操作だけを営業に絞る。
  //
  // 一度も経路を組んでいない予約では問い合わせない。**`lastNotifiedAt` では
  // 絞れない**——戻したり条件を変えたりすると、組み直したあと再び「未通知」に
  // 出せるように印を落とすため（IT6 レビュー 中）。履歴そのものは残るので、
  // 旅程と同じ「一度でも経路を組んだか」で問い合わせる。
  const notified = everRouted;
  // 届いたら取り直しを止める。
  useEffect(() => {
    if (notified) {
      setAwaitingNotificationProjection(false);
    }
  }, [notified]);
  const notifications = useQuery({
    queryKey: ['booking', bookingId, 'notifications'],
    queryFn: () => fetchBookingNotifications(bookingId),
    enabled: notified,
  });
  // **1 つの読み口で画面全体を落とさない。** 予約詳細は 4 つの読み口を束ねる。
  const notificationItems = notifications.data?.state === 'ready'
    ? notifications.data.value.items ?? [] : [];

  const notifiable = data?.state === 'ready' && canNotifyShipper(data.value.routingStatus);
  // **確定は予約の状態の判断。** 遷移表がそのまま答えになる（ROUTE_NOTIFIED から
  // だけ CONFIRMED に進める）ので、別の述語を作らない。写しが増えるほどずれる。
  const confirmable = data?.state === 'ready'
    && canTransitionTo(data.value.bookingStatus, 'CONFIRMED');
  // **発行は経路設計者の操作**（ui_design.md S22）。営業に開くと、経路設計者の
  // 手番を飛ばして発行できてしまう。二重発行も同じ判定で断る。
  const issuable = data?.state === 'ready'
    && canIssueTrackingNumber(data.value.bookingStatus);
  const returnable = data?.state === 'ready' && canReturnToRouting(data.value.bookingStatus);

  const [recipient, setRecipient] = useState('');
  const [returning, setReturning] = useState(false);
  const [returnReason, setReturnReason] = useState('');
  const [returnError, setReturnError] = useState('');

  // 通知する内容は旅程から作る。**画面で打たせない**——打ち直すと、実際の旅程と
  // 違うことを伝えられる。料金概算は US21（IT13）が正典で、いまは欄を置かない
  // （0 円と読まれる）。
  const summary = itinerary.data?.state === 'ready'
    ? summaryOf(itinerary.data.value.legs) : '';

  const notify = useMutation({
    mutationFn: () => notifyShipper(bookingId, { recipientEmail: recipient, summary }),
    onSuccess: async () => {
      setAwaitingNotificationProjection(true);
      await queries.invalidateQueries({ queryKey: ['booking', bookingId] });
      await queries.invalidateQueries({ queryKey: ['booking', bookingId, 'notifications'] });
    },
  });

  const confirm = useMutation({
    mutationFn: () => confirmBooking(bookingId),
    onSuccess: async () => {
      await queries.invalidateQueries({ queryKey: ['booking', bookingId] });
    },
  });

  const issue = useMutation({
    mutationFn: () => issueTrackingNumber(bookingId),
    onSuccess: async () => {
      await queries.invalidateQueries({ queryKey: ['booking', bookingId] });
    },
  });

  const sendBack = useMutation({
    mutationFn: () => returnToRouting(bookingId, returnReason),
    onSuccess: async () => {
      setReturning(false);
      setReturnReason('');
      await queries.invalidateQueries({ queryKey: ['booking', bookingId] });
    },
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
              {/* 追跡番号（US14）。**発行するまで欄そのものを出さない**——
                  空欄は「番号が消えた」と読める。荷主に伝える唯一の手掛かりなので、
                  発行後は誰が見ても読めるようにする（ロールで隠さない）。 */}
              {data.value.trackingNumber && (
                <Row label="追跡番号" value={data.value.trackingNumber} />
              )}
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

          {/* 区間があるかどうかで出す。設計し直しの途中でも読める。 */}
          {everRouted
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

          {/* 通知の操作は営業だけ（US12）。読むのは全員——経路設計者も追跡も
              「荷主に何を伝えたか」を知る必要がある。 */}
          {isSales && notifiable && (
            <div>
              <h2 className={SECTION_TITLE}>荷主への通知</h2>
              <p className="mt-2 text-sm text-gray-600">
                <b>このシステムは送信しません。</b>通知は電話・メールで行い、ここには
                「いつ・誰に・何を伝えたか」の記録だけを残します
              </p>
              <div className="mt-2 space-y-3">
                <label className={LABEL}>
                  <span>通知先メールアドレス</span>
                  <input
                    className={FIELD}
                    type="email"
                    value={recipient}
                    onChange={(event) => setRecipient(event.target.value)}
                  />
                </label>
                <label className={LABEL}>
                  {/* 内容は旅程から作る。打ち直せると、実際の旅程と違うことを
                      伝えられる。料金概算は US21（IT13）が正典なので置かない。 */}
                  <span>通知内容</span>
                  <textarea className={FIELD} rows={2} value={summary} readOnly />
                </label>
                {notify.isError && (
                  <p role="alert" className={ALERT}>
                    {notify.error instanceof ApiError
                      ? notify.error.body.message
                      : '通知を記録できませんでした'}
                  </p>
                )}
                <button
                  type="button"
                  className={BUTTON_PRIMARY}
                  disabled={notify.isPending || summary === ''}
                  onClick={() => notify.mutate()}
                >
                  {notify.isPending ? '記録しています…' : '通知した記録を残す'}
                </button>
              </div>
            </div>
          )}

          {/* 確定の操作は営業だけ（US13）。荷主の承認を確認するのは営業の仕事。
              **通知していない予約には出さない**——押してから断られる導線にしない。 */}
          {isSales && confirmable && (
            <div className="space-y-2">
              <h2 className={SECTION_TITLE}>予約の確定</h2>
              <p className="text-sm text-gray-600">
                荷主の承認を確認してから確定してください。
                <b>確定すると経路設計へは戻せません。</b>
                荷主が変更を求めたら、確定する前に戻します
              </p>
              {confirm.isError && (
                <p role="alert" className={ALERT}>
                  {confirm.error instanceof ApiError
                    ? confirm.error.body.message
                    : '予約を確定できませんでした'}
                </p>
              )}
              <button
                type="button"
                className={BUTTON_PRIMARY}
                disabled={confirm.isPending}
                onClick={() => confirm.mutate()}
              >
                {confirm.isPending ? '確定しています…' : '予約を確定する'}
              </button>
            </div>
          )}

          {/* 追跡番号（US14）。**発行は経路設計者の操作**で、営業には出さない。
              発行済みなら番号を出し、操作は消す（二重に発行しない）。 */}
          {isRouting && issuable && (
            <div className="space-y-2">
              <h2 className={SECTION_TITLE}>追跡番号の発行</h2>
              <p className="text-sm text-gray-600">
                発行すると荷主が輸送状況を追えるようになります。
                <b>番号はシステムが採ります。</b>
                一度発行した予約に二度目は発行できません
              </p>
              {issue.isError && (
                <p role="alert" className={ALERT}>
                  {issue.error instanceof ApiError
                    ? issue.error.body.message
                    : '追跡番号を発行できませんでした'}
                </p>
              )}
              <button
                type="button"
                className={BUTTON_PRIMARY}
                disabled={issue.isPending}
                onClick={() => issue.mutate()}
              >
                {issue.isPending ? '発行しています…' : '追跡番号を発行する'}
              </button>
            </div>
          )}

          {/* 通知したあとだけ開く。通知前に組み直したいなら、経路設計者が自分で
              確定し直せばよい（判定は集約と同じ述語を呼ぶ）。 */}
          {isSales && returnable && (
            <div className="space-y-2">
              {!returning && (
                <button
                  type="button"
                  className={BUTTON_SECONDARY}
                  onClick={() => setReturning(true)}
                >
                  経路設計へ戻す
                </button>
              )}
              {returning && (
                <div className={`${CARD} space-y-2`}>
                  <p className="text-sm">
                    荷主が経路の変更を求めたときに戻します。<b>確定した旅程は
                    そのまま残ります。</b>
                  </p>
                  <label htmlFor="return-reason" className={LABEL}>
                    戻す理由
                  </label>
                  <input
                    id="return-reason"
                    className={FIELD}
                    value={returnReason}
                    onChange={(event) => setReturnReason(event.target.value)}
                  />
                  {returnError && (
                    <p role="alert" className={ALERT}>
                      {returnError}
                    </p>
                  )}
                  {sendBack.isError && (
                    <p role="alert" className={ALERT}>
                      経路設計へ戻せませんでした
                    </p>
                  )}
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className={BUTTON_PRIMARY}
                      disabled={sendBack.isPending}
                      onClick={() => {
                        if (!returnReason.trim()) {
                          // 集約も断るが、押してから 422 で気づく形にしない。
                          setReturnError('戻す理由を入力してください');
                          return;
                        }
                        setReturnError('');
                        sendBack.mutate();
                      }}
                    >
                      戻すことを確定する
                    </button>
                    <button
                      type="button"
                      className={BUTTON_SECONDARY}
                      onClick={() => {
                        setReturning(false);
                        setReturnError('');
                      }}
                    >
                      やめる
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* **1 つの読み口で画面全体を落とさない。** 予約詳細は 4 つの読み口を
              束ねるので、どれか 1 つが思わぬ形を返すと予約の内容ごと消える。 */}
          {notificationItems.length > 0 && (
            <div>
              <h2 className={SECTION_TITLE}>通知履歴</h2>
              <div className="mt-2 overflow-x-auto">
                <table className={TABLE}>
                  <caption className={TABLE_CAPTION}>新しい通知が先に並んでいます</caption>
                  <thead>
                    <tr>
                      <th scope="col" className={TH}>いつ</th>
                      <th scope="col" className={TH}>誰が</th>
                      <th scope="col" className={TH}>宛先</th>
                      <th scope="col" className={TH}>内容</th>
                    </tr>
                  </thead>
                  <tbody>
                    {notificationItems.map((item) => (
                      <tr key={item.notifiedAt} data-testid={`notification-${item.notifiedAt}`}>
                        <td className={TD}>{formatBusinessDateTime(item.notifiedAt)}</td>
                        {/* 誰が通知したか分からないことは「—」で表す（記録は残る）。 */}
                        <td className={TD}>{item.notifiedBy ?? '—'}</td>
                        <td className={TD}>{item.recipientEmail}</td>
                        <td className={TD}>{item.summary}</td>
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

/**
 * 通知する内容を旅程から作る（US12 §受入基準 2）。
 *
 * <p>経由港・所要日数・到着予定日を並べる。<b>料金概算は含めない</b>——料金表は
 * US21（IT13）が正典で、現時点で存在しない。0 と出すと「費用 0 円」と読める。</p>
 */
function summaryOf(legs: readonly ItineraryLegView[]): string {
  const first = legs[0];
  const last = legs.at(-1);
  if (!first || !last) {
    return '';
  }
  const ports = [first.loadUnLocode, ...legs.map((leg) => leg.unloadUnLocode)].join(' → ');
  const days = Math.ceil(
    (new Date(last.unloadAt).getTime() - new Date(first.loadAt).getTime())
    / (24 * 60 * 60 * 1000),
  );
  return `${ports} / 所要 ${days} 日 / 到着予定 ${formatBusinessDateTime(last.unloadAt)}`;
}

function Row({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="flex gap-2">
      <dt className="w-32 shrink-0 text-sm text-gray-600">{label}</dt>
      <dd className="text-sm text-gray-900">{value}</dd>
    </div>
  );
}
