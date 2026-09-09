import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import {
  ALERT,
  BUTTON_PRIMARY,
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
import { cargoTypeLabel } from '@/features/bookings/api';
import { businessLocalToInstant } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import {
  fetchAwaitingClaim,
  fetchCargoSnapshot,
  fetchCargosOnVoyage,
  registerHandling,
  requiresConsigneeConfirmation,
  type HandlingType,
  HANDLING_TYPE_LABELS,
  SELECTABLE_HANDLING_TYPES,
} from './api';

/**
 * 荷役作業記録（S50 / UC13・US15）。**航海起点・連続記録**。
 *
 * <p>荷役作業員は 1 隻から 20〜50 本を連続で記録します。画面の作りはこの使い方から
 * 決まります——<b>記録しても種別と場所は保ち、貨物だけを空にする</b>。</p>
 *
 * <p><b>反映を待たない</b>（ui_design.md S50）。1 本ごとに投影を待つと現場が止まる
 * ので、コマンドの応答で完了とします。</p>
 *
 * <p><b>冪等キーは画面が作る。</b> サーバが採ると、通信断で再送したときに別の鍵に
 * なって二重に記録されます。現場は電波の届かない岸壁で使います。</p>
 */
export function HandlingRecordPage() {
  const { voyageNumber = '' } = useParams();
  const [params] = useSearchParams();
  const unLocode = (params.get('unLocode') ?? '').toUpperCase();
  // **引取は航海起点では辿り着けない**（船から降りたあとの作業で、どの航海の
  // 仕事でもない）。引取待ち（S54）から来たときは、その港で引取を待っている
  // 貨物を対象にする。航海番号は要らない。
  const claimOnly = voyageNumber === '';
  const queries = useQueryClient();

  const [handlingType, setHandlingType] = useState<HandlingType>(
    claimOnly ? 'CLAIM' : 'UNLOAD');
  // 引取待ちの行から来たときは、その貨物を最初から入れておく
  // （追跡番号を書き写させない）。
  const [trackingNumber, setTrackingNumber] = useState(
    (params.get('trackingNumber') ?? '').toUpperCase());
  // **1 本ぶんの鍵は送信のたびに変えない。** 応答が返らずもう一度押したとき、
  // ここで作り直すと別の鍵になって二重に記録される（現場は電波の届かない
  // 岸壁で使う）。次の 1 本へ移るとき——成功したときだけ——採り直す。
  const [activityId, setActivityId] = useState(() => crypto.randomUUID());
  // **起きた日時を後から入れられる**（US15 §受入基準 3）。通信できない場所では
  // 紙に控えて、戻ってから入れる。空なら「いま」。
  const [completedAt, setCompletedAt] = useState('');
  // **荷受人の確認は引取のときだけ**（US16 §受入基準 1）。署名または確認コード。
  const [consigneeName, setConsigneeName] = useState('');

  const needsConsignee = requiresConsigneeConfirmation(handlingType);

  const cargos = useQuery({
    queryKey: ['handling-cargos', voyageNumber, unLocode, claimOnly],
    queryFn: () => (claimOnly
      ? fetchAwaitingClaim(unLocode)
      : fetchCargosOnVoyage(voyageNumber, unLocode)),
    enabled: unLocode !== '',
  });

  // 追跡番号を入れた直後に照合する。**押す前に予定外を知らせる**ため。
  const scanned = useQuery({
    queryKey: ['handling-cargo', trackingNumber, unLocode],
    // **港も渡す。** 予定外かどうかはサーバが答える（H.5）。
    queryFn: () => fetchCargoSnapshot(trackingNumber, unLocode),
    enabled: trackingNumber.trim().length >= 4,
    retry: false,
  });

  const record = useMutation({
    mutationFn: () => registerHandling({
      // 画面が作る冪等キー。**再送しても同じ鍵**（状態に持つ）。
      activityId,
      trackingNumber: trackingNumber.trim().toUpperCase(),
      handlingType,
      unLocode,
      voyageNumber: requiresVoyage(handlingType) ? voyageNumber : null,
      completedAt: completedAt === '' ? null : businessLocalToInstant(completedAt),
      // **引取のときだけ載せる。** 他の種別に載せるとサーバが断る
      // （黙って捨てると、現場は確認を取ったつもりのまま記録が残らない）。
      ...(needsConsignee ? { consigneeName: consigneeName.trim() } : {}),
    }),
    onSuccess: () => {
      // **種別と場所は保つ。** 次の 1 本へすぐ移れるようにする。
      setTrackingNumber('');
      setCompletedAt('');
      setConsigneeName('');
      setActivityId(crypto.randomUUID());
      queries.invalidateQueries({
        queryKey: ['handling-cargos', voyageNumber, unLocode, claimOnly] });
    },
  });

  if (unLocode === '') {
    return (
      <div>
        <h1 className={PAGE_TITLE}>荷役の記録</h1>
        <output className={`${NOTICE} mt-4`}>
          港を選んでください。この船がどの港で積み降ろすかが決まらないと、対象の貨物を出せません。
        </output>
      </div>
    );
  }

  const cargo = scanned.data?.state === 'ready' ? scanned.data.value : null;
  const notFound = scanned.isError
    && scanned.error instanceof ApiError && scanned.error.status === 404;
  // **判定はサーバが答える**（H.5）。画面に書き直すと、本番と画面が別の判定を持つ。
  const offRoute = cargo?.offRouteByType?.[handlingType] ?? false;
  const items = cargos.data?.state === 'ready' ? cargos.data.value.items : [];
  // **選んでいる種別で数える**（M14）。荷降しを済ませただけで引取まで済んだように
  // 見えると、その貨物は誰にも引き取られないまま「済」になる。
  const remaining = items.filter((item) => !item.handledTypes.includes(handlingType));

  return (
    <div>
      <h1 className={PAGE_TITLE}>
        {claimOnly
          ? `引取の記録\u3000${unLocode}`
          : `荷役の記録\u3000航海 ${voyageNumber}\u3000${unLocode}`}
      </h1>
      <p className="mt-1 text-sm text-gray-600">
        {claimOnly
          ? `${unLocode} で引取を待っている貨物 ${items.length} 本（未記録 ${remaining.length} 本）`
          : `${unLocode} で扱う予定 ${items.length} 本（未記録 ${remaining.length} 本）`}
      </p>

      <section className={`${CARD} mt-4`}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="handlingType" className={LABEL}>
              作業種別
            </label>
            <select
              id="handlingType"
              className={FIELD}
              value={handlingType}
              onChange={(event) => setHandlingType(event.target.value as HandlingType)}
            >
              {(claimOnly ? ['CLAIM' as HandlingType] : SELECTABLE_HANDLING_TYPES).map((type) => (
                <option key={type} value={type}>
                  {HANDLING_TYPE_LABELS[type]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="trackingNumber" className={LABEL}>
              追跡番号
            </label>
            <input
              id="trackingNumber"
              className={FIELD}
              value={trackingNumber}
              onChange={(event) => setTrackingNumber(event.target.value.toUpperCase())}
              placeholder="TRK-AB12CD3456"
              autoComplete="off"
            />
            {/* スキャンはバーコード読取機がキーボード入力として送る。
                カメラ撮影は本 IT で作らない。 */}
            <p className="mt-1 text-xs text-gray-600">
              スキャンした番号はここに入ります。手入力もできます。
            </p>
          </div>
          <div>
            <label htmlFor="completedAt" className={LABEL}>
              作業日時
            </label>
            <input
              id="completedAt"
              type="datetime-local"
              className={FIELD}
              value={completedAt}
              onChange={(event) => setCompletedAt(event.target.value)}
            />
            {/* **後から入れられる**（US15 §受入基準 3）。電波の届かない岸壁では
                紙に控え、戻ってから入れる。未来は集約が断る。 */}
            <p className="mt-1 text-xs text-gray-600">
              空のままなら「いま」で記録します。紙に控えた作業は日時を入れてください。
            </p>
          </div>
        </div>

        {needsConsignee && (
          <div className="mt-4">
            <label htmlFor="consigneeName" className={LABEL}>
              荷受人の確認（署名または確認コード）
            </label>
            <input
              id="consigneeName"
              className={FIELD}
              value={consigneeName}
              onChange={(event) => setConsigneeName(event.target.value)}
              placeholder="受領者名または確認コード"
              autoComplete="off"
            />
            {/* **引取は貨物状態を引取済——精算の開始条件——まで進める。**
                そこからは戻せないので、確認が取れていないものは送らせない。 */}
            <p className="mt-1 text-xs text-gray-600">
              引き渡しの証明になります。確認が取れていない引取は記録できません。
            </p>
          </div>
        )}

        {cargo !== null && (
          <dl className="mt-4 grid grid-cols-[6rem_1fr] gap-y-1 text-sm">
            <dt className="text-gray-600">確認</dt>
            <dd className="text-gray-900">
              {cargo.originUnLocode} → {cargo.destinationUnLocode}{'\u3000'}
              {cargoTypeLabel(cargo.cargoType)}
            </dd>
          </dl>
        )}

        {notFound && (
          <output className={`${ALERT} mt-4`}>
            この追跡番号の貨物が見つかりません。番号をお確かめください。
          </output>
        )}

        {offRoute && (
          // **押す前に知らせる。** 押してから警告すると、作業員は取り消しの手間を負う。
          <output className={`${NOTICE} mt-4`}>
            {unLocode} はこの貨物の予定ルートに含まれていません。
            予定外として記録し、追跡と予約に知らせます。
          </output>
        )}

        <button
          type="button"
          className={`${BUTTON_PRIMARY} mt-4`}
          disabled={cargo === null || record.isPending
            || (needsConsignee && consigneeName.trim() === '')}
          onClick={() => record.mutate()}
        >
          記録する
        </button>

        {record.isError && (
          <output className={`${ALERT} mt-4`}>
            {record.error instanceof ApiError
              ? record.error.body.message
              : '記録できませんでした。もう一度お試しください。'}
          </output>
        )}
      </section>

      <section className={`${CARD} mt-4 overflow-x-auto`}>
        <h2 className={SECTION_TITLE}>
          {claimOnly ? '引取を待っている貨物' : 'この港で扱う貨物'}
        </h2>
        {items.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">
            {claimOnly
              ? 'この港で引取を待っている貨物はありません。'
              : 'この航海がこの港で扱う貨物はありません。航海番号と港をお確かめください。'}
          </p>
        ) : (
          <table className={`${TABLE} mt-2`}>
            <caption className={TABLE_CAPTION}>この港で扱う貨物</caption>
            <thead>
              <tr>
                <th className={TH}>追跡番号</th>
                <th className={TH}>区間</th>
                <th className={TH}>状態</th>
                <th className={TH}>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.trackingNumber}>
                  <td className={TD}>
                    <Link to={`/handling/${item.trackingNumber}`} className={LINK}>
                      {item.trackingNumber}
                    </Link>
                  </td>
                  <td className={TD}>
                    {item.originUnLocode} → {item.destinationUnLocode}
                  </td>
                  {/* **選んでいる種別で見る**（M14）。同じ港で荷降し → 引取が起きる。 */}
                  <td className={TD}>{handledLabel(item.handledTypes, handlingType)}</td>
                  <td className={TD}>
                    {/* **一覧の行から記録を始められる**（M15）。追跡番号を
                        書き写させると、連続記録の途中で打ち間違える。 */}
                    <button
                      type="button"
                      className={LINK}
                      onClick={() => setTrackingNumber(item.trackingNumber)}
                    >
                      この貨物を記録する
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

/** 航海番号が要る種別か。<b>サーバの `HandlingType` と同じ判断</b>。 */
function requiresVoyage(type: HandlingType): boolean {
  return type === 'LOAD' || type === 'UNLOAD';
}

/**
 * この港での状態（S50 の一覧）。
 *
 * <p><b>選んでいる種別で見る</b>（M14）。引取が入ると同じ港で荷降し → 引取が
 * 起きるので、「記録済」の一語では「荷降しは済んだが引取はまだ」を表せない。
 * 済んだ種別が他にあるならその呼び名を出す——何が終わっているかが読めないと、
 * 作業員は同じ貨物をもう一度探す。</p>
 */
function handledLabel(handled: readonly HandlingType[], selected: HandlingType): string {
  if (handled.includes(selected)) {
    return `${HANDLING_TYPE_LABELS[selected]}済`;
  }
  if (handled.length > 0) {
    return `未記録（${handled.map((type) => HANDLING_TYPE_LABELS[type]).join('・')}済）`;
  }
  return '未記録';
}
