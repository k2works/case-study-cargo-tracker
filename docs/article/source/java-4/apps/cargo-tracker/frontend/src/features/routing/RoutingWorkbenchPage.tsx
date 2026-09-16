import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
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
  TD,
  TH,
} from '@/shared/ui/styles';
import { ApiError } from '@/shared/api/client';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import {
  assignRoute,
  bookingStatusLabel,
  cargoTypeLabel,
  fetchBooking,
} from '@/features/bookings/api';
import { canAssignRoute, canRequestConditionReview } from '@/features/bookings/transitions';
import {
  adjustRouteSpecification,
  fetchRouteCandidates,
  requestConditionReview,
  type RouteCandidateView,
} from './api';

/**
 * S31 経路設計ワークベンチ（UC06 / US08・US09）。
 *
 * <p>作業一覧（S30）から予約を開き、候補を見て経路を確定する。</p>
 *
 * <p><b>「候補が無い」と「探せなかった」を言い分ける。</b> 探索できなかったときは
 * サーバが 503 を返す。空の候補一覧にすると、経路設計者は直らない条件を変え続ける。</p>
 *
 * <p><b>打ち切りも黙らない。</b> 上限まで探したことを出さないと、上限を超える経路
 * しか無い予約が「候補 0 件」に見える（ADR-0007）。</p>
 */
export function RoutingWorkbenchPage() {
  const { bookingId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<string>('');
  const [selectionError, setSelectionError] = useState('');
  const booking = useQuery({
    queryKey: ['booking', bookingId],
    queryFn: () => fetchBooking(bookingId),
    refetchInterval: (query) => (query.state.data?.state === 'pending' ? 2000 : false),
  });
  const candidates = useQuery({
    queryKey: ['route-candidates', bookingId],
    queryFn: () => fetchRouteCandidates(bookingId),
    retry: false,
  });

  // 条件の入力欄。サーバが返した条件で初期化する。**画面が条件を持たない。**
  // 画面から組み立てて候補算出へ渡すと、誰がいつ期限を延ばしたかが残らない。
  const [deadline, setDeadline] = useState('');
  const [excluded, setExcluded] = useState('');
  const [departFrom, setDepartFrom] = useState('');
  const [sendingBack, setSendingBack] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');

  // **条件は予約から組む。候補算出の応答からは組まない。**
  // 候補の応答に載せると、探索が落ちている（503）間だけ条件の欄と差し戻しが
  // 画面から消える。直せる手段が要るのはまさにそのときで、経路設計者は
  // 「探索が直るのを待つ」以外に何もできなくなる（IT6 引き継ぎ 8b）。
  const condition = booking.data?.state === 'ready' ? booking.data.value : null;
  // サーバの条件が変わったら欄も追随する。入力中の値を握り続けると、再算出の
  // あとも古い値が残って「送ったのに変わらない」ように見える。
  useEffect(() => {
    if (!condition) {
      return;
    }
    setDeadline(condition.arrivalDeadline);
    setExcluded(condition.routeExcludeUnLocodes.join(', '));
    setDepartFrom(condition.routeDepartFromUnLocode ?? '');
  }, [condition]);

  const adjust = useMutation({
    mutationFn: () => adjustRouteSpecification(bookingId, {
      arrivalDeadline: deadline,
      excludeUnLocodes: parsePorts(excluded),
      departFromUnLocode: departFrom.trim() === '' ? null : departFrom.trim().toUpperCase(),
    }),
    onSuccess: async () => {
      // 条件を記録してから候補を取り直す。順序が逆だと、古い条件の候補が出る。
      await queryClient.invalidateQueries({ queryKey: ['booking', bookingId] });
      await queryClient.invalidateQueries({ queryKey: ['route-candidates', bookingId] });
    },
  });

  const sendBack = useMutation({
    mutationFn: () => requestConditionReview(bookingId, reason),
    onSuccess: async () => {
      setSendingBack(false);
      setReason('');
      await queryClient.invalidateQueries({ queryKey: ['booking', bookingId] });
    },
  });

  const assign = useMutation({
    mutationFn: (legs: RouteCandidateView['legs']) => assignRoute(bookingId, legs),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['booking', bookingId] });
      // 確定したら予約詳細へ戻す。旅程はそこで読む（S22）。
      navigate(`/bookings/${bookingId}`);
    },
  });

  // 経路を確定できる状態か。集約が受けるのは ROUTING_REQUESTED と MISROUTED だけ。
  const assignable = booking.data?.state === 'ready'
    && canAssignRoute(booking.data.value.routingStatus);
  // **差し戻せる状態は別の判断。** 誤配は経路を確定し直せるが、差し戻せない
  // （ADR-0009 決定 2）。同じ述語で出し分けると誤配で押せて 422 になる。
  const sendBackable = booking.data?.state === 'ready'
    && canRequestConditionReview(booking.data.value.routingStatus);

  const unavailable =
    candidates.error instanceof ApiError && candidates.error.status === 503;
  const found = candidates.data?.state === 'ready' ? candidates.data.value : null;
  // 超過を承知で確定するとき、1 度だけ確かめる（US28 §受入基準 6）。
  const [pendingOverdue, setPendingOverdue] = useState<RouteCandidateView | null>(null);
  // **超過の列は誤配の再設計でだけ出す。** 通常の設計では全候補が期限を
  // 満たすので、常に「期限内」と並ぶ列は読む人の目を無駄に使う。
  const overdue = found?.candidates.some((candidate) => candidate.overdueDays > 0) ?? false;
  // 誤配の再設計か（US28）。起点は現在地に固定され、編集できない。
  const redesigning = booking.data?.state === 'ready'
    && booking.data.value.routingStatus === 'MISROUTED';
  const currentLocation = booking.data?.state === 'ready'
    ? booking.data.value.lastHandlingUnLocode : null;

  return (
    <section>
      <h1 className={PAGE_TITLE}>経路設計</h1>

      {booking.data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4`}>{booking.data.message}</output>
      )}
      {booking.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          予約を取得できませんでした
        </p>
      )}

      {booking.data?.state === 'ready' && (
        <div className={`${CARD} mt-4 space-y-1 text-sm`}>
          <p>
            予約番号:{' '}
            <Link to={`/bookings/${bookingId}`} className={LINK}>
              {booking.data.value.bookingNumber}
            </Link>
          </p>
          <p>荷主: {booking.data.value.shipperName ?? '—'}</p>
          <p>
            輸送区間: {booking.data.value.originUnLocode} →{' '}
            {booking.data.value.destinationUnLocode}
          </p>
          <p>到着期限: {booking.data.value.arrivalDeadline}</p>
          <p>
            貨物: {booking.data.value.productName}（
            {cargoTypeLabel(booking.data.value.cargoType)}）
          </p>
          <p>状態: {bookingStatusLabel(booking.data.value.bookingStatus)}</p>
        </div>
      )}

      {/* **いまの条件を出す。** 何で絞っているのかが読めないと、経路設計者は
          同じ条件で何度も再算出する。条件はサーバが持ち、ここは映すだけ。 */}
      {condition && (
        <>
          {/* **営業が戻した理由を出す。** 記録だけ残して読み口を出さないと、営業に
          無駄な入力をさせることになる（理由は必須にしている）。同じ経路をもう一度
          確定して、また戻される往復にもなる。 */}
      {booking.data?.state === 'ready' && booking.data.value.returnReason && (
        <output className={`${NOTICE} mt-4 block`}>
          <b>営業から戻されました</b>
          {booking.data.value.returnedToRoutingAt
            ? `（${formatBusinessDateTime(booking.data.value.returnedToRoutingAt)}）`
            : ''}
          : {booking.data.value.returnReason}
        </output>
      )}

      {/* 営業から返ってきた協議の結果（US10 §4 の対）。**頼んだ理由と対で出す**——
          何を頼んだかが読めないと、返事の意味が取れない。差し戻しは経路設計者 →
          営業の一方向しか無く、営業は協議を終えても伝える手段がなかった。 */}
      {booking.data?.state === 'ready' && booking.data.value.conditionReviewRespondedAt && (
        <output className={`${NOTICE} mt-4 block`}>
          <b>営業から返事が来ています</b>
          （{formatBusinessDateTime(booking.data.value.conditionReviewRespondedAt)}）
          <div className="mt-1 text-sm">
            頼んだこと: {booking.data.value.conditionReviewReason}
          </div>
          <div className="mt-1">
            決まったこと: {booking.data.value.conditionReviewResponse}
          </div>
        </output>
      )}

      <h2 className={`${SECTION_TITLE} mt-6`}>探す条件</h2>
          <div className={`${CARD} mt-2 space-y-3`}>
            <div className="grid gap-3 sm:grid-cols-3">
              <label className={LABEL}>
                <span>到着期限</span>
                <input
                  className={FIELD}
                  type="date"
                  value={deadline}
                  onChange={(event) => setDeadline(event.target.value)}
                />
              </label>
              <label className={LABEL}>
                <span>除外する港</span>
                <input
                  className={FIELD}
                  value={excluded}
                  placeholder="SGSIN, HKHKG"
                  onChange={(event) => setExcluded(event.target.value)}
                />
              </label>
              <label className={LABEL}>
                <span>{redesigning ? '出発港（現在地）' : 'この港より後に出る便だけ'}</span>
                {/* **誤配の再設計では起点を編集させない**（US28 §受入基準 4）。
                    サーバは現在地を優先するので、編集できると「入れても効かない欄」
                    になる。押せない操作を並べないのと同じ理由（IT11 レビュー 高）。 */}
                <input
                  className={FIELD}
                  value={redesigning ? (currentLocation ?? '') : departFrom}
                  placeholder="JPOSA"
                  readOnly={redesigning}
                  aria-readonly={redesigning}
                  onChange={(event) => setDepartFrom(event.target.value)}
                />
              </label>
            </div>
            <p className="text-sm text-gray-600">
              期限はここから延ばせます。仮受付を過ぎた予約は予約修正の画面では直せません
            </p>
            {redesigning && (
              <p className="text-sm text-gray-600">
                誤配のため、出発港は貨物の<b>現在地</b>に固定されています。
                目的地は元の予約のままです。期限を延ばすと、超過日数はその期限で数え直されます
              </p>
            )}
            {adjust.isError && (
              <p role="alert" className={ALERT}>
                {adjust.error instanceof ApiError
                  ? adjust.error.body.message
                  : '条件を変えられませんでした'}
              </p>
            )}
            <div className="flex gap-2">
              <button
                type="button"
                className={BUTTON_PRIMARY}
                disabled={adjust.isPending}
                onClick={() => adjust.mutate()}
              >
                {adjust.isPending ? '再算出しています…' : '条件を変えて再算出'}
              </button>
              {/* 組めているのだから見直しは要らない。押してから断られる導線に
                  しない（判定は集約と同じ述語を呼ぶ）。**誤配も差し戻せない。** */}
              {sendBackable && !sendingBack && (
                <button
                  type="button"
                  className={BUTTON_SECONDARY}
                  onClick={() => setSendingBack(true)}
                >
                  営業へ差し戻す
                </button>
              )}
            </div>
            {sendingBack && (
              <div className="space-y-2 border-t border-gray-200 pt-3">
                <p className="text-sm">
                  条件を変えても組めないときは、営業へ見直しを頼めます。<b>予約は
                  このまま経路設計の作業一覧に残ります。</b>
                </p>
                <label htmlFor="review-reason" className={LABEL}>
                  差し戻す理由
                </label>
                <input
                  id="review-reason"
                  className={FIELD}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
                {reasonError && (
                  <p role="alert" className={ALERT}>
                    {reasonError}
                  </p>
                )}
                {sendBack.isError && (
                  <p role="alert" className={ALERT}>
                    差し戻せませんでした
                  </p>
                )}
                <div className="flex gap-2">
                  <button
                    type="button"
                    className={BUTTON_PRIMARY}
                    disabled={sendBack.isPending}
                    onClick={() => {
                      if (!reason.trim()) {
                        // 集約も断るが、押してから 422 で気づく形にしない。
                        setReasonError('差し戻す理由を入力してください');
                        return;
                      }
                      setReasonError('');
                      sendBack.mutate();
                    }}
                  >
                    差し戻しを送る
                  </button>
                  <button
                    type="button"
                    className={BUTTON_SECONDARY}
                    onClick={() => {
                      setSendingBack(false);
                      setReasonError('');
                    }}
                  >
                    やめる
                  </button>
                </div>
              </div>
            )}
          </div>
        </>
      )}

      <h2 className={`${SECTION_TITLE} mt-6`}>経路候補</h2>

      {candidates.isPending && <output className={`${NOTICE} mt-2`}>候補を探しています…</output>}

      {/* 「探せなかった」を「候補が無い」に見せない。条件を変えても直らない。 */}
      {unavailable && (
        <p role="alert" className={`${ALERT} mt-2`}>
          経路設計サービスに問い合わせできませんでした。しばらくしてからもう一度お試しください
        </p>
      )}
      {candidates.isError && !unavailable && (
        <p role="alert" className={`${ALERT} mt-2`}>
          経路候補を取得できませんでした
        </p>
      )}

      {/* **0 件と打ち切りを重ねて出さない。** 重ねると「期限を延ばす・港を広げる」と
          「条件を絞る」が同時に出て、逆のことを勧めることになる（IT5 レビュー 高 1）。
          0 件で打ち切りに当たったのは、乗り継ぎの上限で枝を捨てたときだけなので、
          条件を変えても候補は増えない。 */}
      {found?.candidates.length === 0 && !found.truncated && (
        <output className={`${NOTICE} mt-2 block`}>
          期限内に到着できる経路が見つかりませんでした。到着期限を延ばすか、経由できる港を
          広げると候補が出ることがあります
        </output>
      )}

      {found?.candidates.length === 0 && found.truncated && (
        <output className={`${NOTICE} mt-2 block`}>
          乗り継ぎを 4 回以上必要とする経路しかありません。{' '}
          <b>条件を変えても候補は増えません。</b>手配の相談が要ります
        </output>
      )}

      {/* 上限まで探したことを黙らない（ADR-0007）。黙ると「候補が無い」と読まれる。
          **「乗り継ぎの多い経路は出していません」とは書かない。** 打ち切りは並べた
          あとに効くので、出ていないのは推奨順の 21 位以下であって、乗り継ぎの多さで
          落としたものではない（ADR-0007「決定 2 の訂正」）。そう書くと「条件を絞れば
          良い候補が出る」と読まれて空振りする。 */}
      {found && found.candidates.length > 0 && found.truncated && (
        <output className={`${NOTICE} mt-2 block`}>
          候補が多いため、<b>推奨順の上位 20 件だけ</b>を出しています。条件を絞ると
          別の候補が上位に入ることがあります
        </output>
      )}

      {found && found.candidates.length > 0 && (
        <div className={`${CARD} mt-2 overflow-x-auto`}>
          <table className={TABLE}>
            {/* 並び順の根拠は目でも読めるようにする。読み上げにしか無いと、
                なぜこの順なのかが分からないまま上から選ばれる。 */}
            <caption className="caption-top pb-2 text-left text-sm text-gray-600">
              {overdue
                ? '期限を満たす候補を先に、そのあと直行便・所要時間の短い順に並んでいます'
                : '直行便を先に、そのあと所要時間の短い順に並んでいます'}
            </caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>選択</th>
                <th scope="col" className={TH}>推奨</th>
                <th scope="col" className={TH}>所要日数</th>
                <th scope="col" className={TH}>経由港</th>
                <th scope="col" className={TH}>航海</th>
                <th scope="col" className={TH}>出発 → 到着</th>
                {/* **超過は誤配の再設計でだけ出る。** 通常の設計では全候補が
                    期限を満たすので、列そのものを出さない（常に「—」の列は
                    読む人の目を無駄に使う）。 */}
                {overdue && <th scope="col" className={TH}>期限超過</th>}
              </tr>
            </thead>
            <tbody>
              {found.candidates.map((candidate, index) => (
                <tr key={candidateKey(candidate)} data-testid={`candidate-${index + 1}`}>
                  <td className={TD}>
                    <input
                      type="radio"
                      name="route-candidate"
                      aria-label={`候補 ${index + 1}`}
                      value={candidateKey(candidate)}
                      checked={selected === candidateKey(candidate)}
                      onChange={(event) => {
                        setSelected(event.target.value);
                        setSelectionError('');
                      }}
                    />
                  </td>
                  <td className={TD}>
                    {index + 1}
                    {candidate.direct ? '（直行便）' : ''}
                  </td>
                  <td className={TD}>{candidate.transitDays} 日</td>
                  <td className={TD}>{viaPortsOf(candidate)}</td>
                  <td className={TD}>
                    {candidate.legs.map((leg) => leg.voyageNumber).join(' → ')}
                  </td>
                  <td className={TD}>{journeyOf(candidate)}</td>
                  {overdue && (
                    <td className={TD}>
                      {candidate.overdueDays > 0 ? (
                        <span className="font-semibold text-red-700">
                          {candidate.overdueDays} 日超過
                        </span>
                      ) : (
                        '期限内'
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* 確定できない状態で押せるボタンを出さない。押してから断られる導線にしない
          （IT5 レビュー 中 5・7）。判定は集約と同じ述語を呼ぶ。 */}
      {found && found.candidates.length > 0 && !assignable && (
        <output className={`${NOTICE} mt-4 block`}>
          この予約は経路が確定しています。確定した旅程は
          <Link to={`/bookings/${bookingId}`} className={LINK}>
            予約詳細
          </Link>
          で読めます
        </output>
      )}

      {found && found.candidates.length > 0 && assignable && (
        <div className="mt-4 space-y-2">
          {selectionError && (
            <p role="alert" className={ALERT}>
              {selectionError}
            </p>
          )}
          {assign.isError && (
            <p role="alert" className={ALERT}>
              {assign.error instanceof ApiError
                ? assign.error.body.message
                : '経路を確定できませんでした'}
            </p>
          )}
          <button
            type="button"
            className={BUTTON_PRIMARY}
            disabled={assign.isPending}
            onClick={() => {
              const candidate = found.candidates.find((c) => candidateKey(c) === selected);
              if (!candidate) {
                setSelectionError('経路候補を選んでください');
                return;
              }
              setSelectionError('');
              if (candidate.overdueDays > 0) {
                // **超過を承知で選んだことを 1 度だけ確かめる**（US28 §受入基準 6）。
                // 押した本人が超過に気づかないまま確定すると、荷主への説明が
                // 「なぜ遅れるのか」から始まらない。
                setPendingOverdue(candidate);
                return;
              }
              assign.mutate(candidate.legs);
            }}
          >
            {/* 複数ロールが触る予約の遷移なので、押したあとは送信中を出す。 */}
            {assign.isPending ? '送信中…' : 'この経路で確定'}
          </button>

          {pendingOverdue && (
            <div role="alertdialog" aria-label="期限超過の確認" className={`${ALERT} space-y-2`}>
              <p>
                この経路は当初の到着期限を <b>{pendingOverdue.overdueDays} 日</b>{' '}
                超えます。確定すると、荷主への連絡にこの差分を含める必要があります。
              </p>
              {/* **荷主への説明は「何日に着くか」で始まる**（IT11 レビュー 中）。
                  日数だけだと、設計者は確定後に予約詳細へ戻って日付を拾い直す。 */}
              <p className="text-sm">
                到着予定{' '}
                <b>
                  {formatBusinessDateTime(
                    pendingOverdue.legs.at(-1)?.unloadTime ?? '')}
                </b>
                （当初の到着期限 {arrivalDeadlineOf(booking.data) ?? '—'}）
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  className={BUTTON_PRIMARY}
                  onClick={() => {
                    const legs = pendingOverdue.legs;
                    setPendingOverdue(null);
                    assign.mutate(legs);
                  }}
                >
                  超過を承知で確定する
                </button>
                <button
                  type="button"
                  className={BUTTON_SECONDARY}
                  onClick={() => setPendingOverdue(null)}
                >
                  やめる
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 費用は料金算出（US21）が正典。0 円と出すより、出ないことを書く。
          利用者はストーリー ID を知らないので、機能の名前で書く。 */}
      <p className="mt-2 text-sm text-gray-600">
        費用はこの画面では出ません。料金の算出は別の画面で行います
      </p>

      <p className="mt-4">
        <Link to="/routing/worklist" className={LINK}>
          経路設計作業一覧へ
        </Link>
      </p>
    </section>
  );
}

/** 予約の到着期限。読めなければ null（画面は「—」と出す）。 */
function arrivalDeadlineOf(data: unknown): string | null {
  const state = data as { state?: string; value?: { arrivalDeadline?: string } } | undefined;
  return state?.state === 'ready' ? state.value?.arrivalDeadline ?? null : null;
}

/**
 * 入力された除外港を港コードの一覧にする。
 *
 * <p>打ち間違いの空白で港が増えないように、空の要素は落とす。大文字に揃えるのは、
 * 港コードが大文字だから（小文字で入れると 1 件も除外されない）。</p>
 */
function parsePorts(input: string): string[] {
  return input
    .split(',')
    .map((port) => port.trim().toUpperCase())
    .filter((port) => port.length > 0);
}

/** 最初の出発から最後の到着まで。区間が 1 本でも同じ形で読める。 */
function journeyOf(candidate: RouteCandidateView): string {
  const first = candidate.legs[0];
  const last = candidate.legs.at(-1);
  if (!first || !last) {
    return '—';
  }
  return `${formatBusinessDateTime(first.loadTime)} → ${formatBusinessDateTime(last.unloadTime)}`;
}

/**
 * 経由港。端点は含まない（一覧の「輸送区間」に出ている）。
 *
 * 経由港を決めるのはここ 1 か所だけ。応答は区間を業務上の順で運ぶので、
 * 最後の区間を除いた到着港がそのまま経由港になる。バックエンドにも
 * 同じ判断を置くと、片方だけ直したときに表示と根拠が食い違う。
 */
function viaPortsOf(candidate: RouteCandidateView): string {
  const via = candidate.legs.slice(0, -1).map((leg) => leg.unloadUnLocode);
  return via.length === 0 ? '—' : via.join(' → ');
}

/** 候補は ID を持たないので、選んだ内容そのものから鍵を作る。 */
function candidateKey(candidate: RouteCandidateView): string {
  return candidate.legs
    .map((leg) => `${leg.voyageNumber}:${leg.loadTime}`)
    .join('|');
}
