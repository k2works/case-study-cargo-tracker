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
import { ApiError } from '@/shared/api/client';
import {
  fetchCargoSnapshot,
  fetchCargosOnVoyage,
  registerHandling,
  type CargoSnapshotView,
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
  const queries = useQueryClient();

  const [handlingType, setHandlingType] = useState<HandlingType>('UNLOAD');
  const [trackingNumber, setTrackingNumber] = useState('');

  const cargos = useQuery({
    queryKey: ['handling-cargos', voyageNumber, unLocode],
    queryFn: () => fetchCargosOnVoyage(voyageNumber, unLocode),
    enabled: unLocode !== '',
  });

  // 追跡番号を入れた直後に照合する。**押す前に予定外を知らせる**ため。
  const scanned = useQuery({
    queryKey: ['handling-cargo', trackingNumber],
    queryFn: () => fetchCargoSnapshot(trackingNumber),
    enabled: trackingNumber.trim().length >= 4,
    retry: false,
  });

  const record = useMutation({
    mutationFn: () => registerHandling({
      // 画面が作る冪等キー。再送しても同じ鍵になる。
      activityId: crypto.randomUUID(),
      trackingNumber: trackingNumber.trim().toUpperCase(),
      handlingType,
      unLocode,
      voyageNumber: requiresVoyage(handlingType) ? voyageNumber : null,
    }),
    onSuccess: () => {
      // **種別と場所は保つ。** 次の 1 本へすぐ移れるようにする。
      setTrackingNumber('');
      queries.invalidateQueries({ queryKey: ['handling-cargos', voyageNumber, unLocode] });
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
  const offRoute = cargo !== null && isOffRoute(cargo, handlingType, unLocode);
  const items = cargos.data?.state === 'ready' ? cargos.data.value.items : [];
  const remaining = items.filter((item) => !item.handledHere);

  return (
    <div>
      <h1 className={PAGE_TITLE}>
        荷役の記録{'\u3000'}航海 {voyageNumber}{'\u3000'}{unLocode}
      </h1>
      <p className="mt-1 text-sm text-gray-600">
        {unLocode} で降ろす予定 {items.length} 本（未記録 {remaining.length} 本）
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
              {SELECTABLE_HANDLING_TYPES.map((type) => (
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
        </div>

        {cargo !== null && (
          <dl className="mt-4 grid grid-cols-[6rem_1fr] gap-y-1 text-sm">
            <dt className="text-gray-600">確認</dt>
            <dd className="text-gray-900">
              {cargo.originUnLocode} → {cargo.destinationUnLocode}{'\u3000'}{cargo.cargoType}
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
          disabled={cargo === null || record.isPending}
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
        <h2 className={SECTION_TITLE}>この港で降ろす貨物</h2>
        {items.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">
            この航海がこの港で降ろす貨物はありません。航海番号と港をお確かめください。
          </p>
        ) : (
          <table className={`${TABLE} mt-2`}>
            <caption className={TABLE_CAPTION}>この港で降ろす貨物</caption>
            <thead>
              <tr>
                <th className={TH}>追跡番号</th>
                <th className={TH}>区間</th>
                <th className={TH}>状態</th>
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
                  <td className={TD}>{item.handledHere ? '記録済' : '未記録'}</td>
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
 * 予定ルート外か（画面の先出し警告）。
 *
 * <p><b>記録の可否はサーバが決める。</b> ここでの判定は「押す前に知らせる」ためだけ
 * で、記録は拒まない。サーバは同じ判定を `CargoSnapshot#isOffRoute` で行う。</p>
 */
function isOffRoute(cargo: CargoSnapshotView, type: HandlingType, unLocode: string): boolean {
  if (type === 'RECEIVE') {
    return cargo.originUnLocode !== unLocode;
  }
  if (type === 'CLAIM') {
    return cargo.destinationUnLocode !== unLocode;
  }
  const ports = cargo.legs.map((leg) => (type === 'LOAD' ? leg.loadUnLocode : leg.unloadUnLocode));
  return ports.length === 0 || !ports.includes(unLocode);
}
