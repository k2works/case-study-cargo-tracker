import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  BUTTON_DANGER,
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
import { acceptedCargoTypeLabel, cancelVoyage, fetchVoyage, formatVoyageTime } from './api';
import type { VoyageView } from './api';
import { canCancel, canUpdateSchedule } from './voyageRules';

/**
 * S34 航海詳細（UC19 / US24・US25）。
 *
 * <p>IT3 のレビューで「登録した中身を確認できない」「重複の案内が指す先が無い」と
 * 指摘された受け皿。更新（S33 の編集）はここから入る。</p>
 *
 * <p><b>更新の導線はキャンセル済みには出さない。</b> 判定は {@code voyageRules} の述語を
 * 呼ぶ。画面に条件を書き直すと、集約を直したときにここが古くなる（予約側の
 * {@code transitions.ts} と同じ形）。</p>
 */
export function VoyageDetailPage() {
  const { voyageNumber = '' } = useParams();
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');
  const cancel = useMutation({
    mutationFn: () => cancelVoyage(voyageNumber, reason),
    onSuccess: () => {
      setCancelling(false);
      setReason('');
      return queryClient.invalidateQueries({ queryKey: ['voyage', voyageNumber] });
    },
  });
  const { data, isPending, isError } = useQuery({
    queryKey: ['voyage', voyageNumber],
    queryFn: () => fetchVoyage(voyageNumber),
    refetchInterval: (query) => (query.state.data?.state === 'pending' ? 3000 : false),
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>航海 {voyageNumber}</h1>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          航海を取得できませんでした
        </p>
      )}
      {/* 投影がまだのときは「見つかりません」と言わない。登録に失敗したと読める。 */}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {data?.state === 'ready' && (
        <>
          <div className={`${CARD} mt-4 space-y-1 text-sm`}>
            <p>運送会社: {data.value.carrierName}（{data.value.carrierCode}）</p>
            <p>船名: {data.value.vesselName}</p>
            <p>
              対応貨物種別: {data.value.acceptedCargoTypes.map(acceptedCargoTypeLabel).join(' / ')}
            </p>
            {data.value.cancelled && (
              <p>
                キャンセル済み
                {data.value.cancelReason ? `: ${data.value.cancelReason}` : ''}
                {cancelledStamp(data.value)}
              </p>
            )}
            {/* 一度も更新していない航海に「最終更新」を出すと、登録日時と
                区別が付かなくなる。直したことのある航海だけに出す。 */}
            {data.value.updatedAt && (
              <p>
                最終更新: {formatVoyageTime(data.value.updatedAt)}
                {data.value.updatedBy ? `（${data.value.updatedBy}）` : ''}
              </p>
            )}
          </div>

          <h2 className={`${SECTION_TITLE} mt-6`}>寄港地</h2>
          <div className={`${CARD} mt-2 overflow-x-auto`}>
            <table className={TABLE}>
              <caption className={TABLE_CAPTION}>寄港する順に並んでいます</caption>
              <thead>
                <tr>
                  <th scope="col" className={TH}>区間</th>
                  <th scope="col" className={TH}>出発地 → 到着地</th>
                  <th scope="col" className={TH}>出発</th>
                  <th scope="col" className={TH}>到着</th>
                </tr>
              </thead>
              <tbody>
                {data.value.movements.map((movement) => (
                  <tr key={movement.movementSeq} data-testid={`movement-${movement.movementSeq}`}>
                    <td className={TD}>{movement.movementSeq}</td>
                    <td className={TD}>
                      {movement.departureUnLocode} → {movement.arrivalUnLocode}
                    </td>
                    <td className={TD}>{formatVoyageTime(movement.departureAt)}</td>
                    <td className={TD}>{formatVoyageTime(movement.arrivalAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 判定は書き直さず述語を呼ぶ（US24）。止めるのは戻せないので、
              一覧のボタン 1 つでは送らず理由を書かせる。 */}
          {canCancel(data.value) && (
            <div className="mt-6">
              {!cancelling && (
                <button
                  type="button"
                  className={BUTTON_SECONDARY}
                  onClick={() => setCancelling(true)}
                >
                  この航海をキャンセルする
                </button>
              )}
              {cancelling && (
                <form
                  className={`${CARD} space-y-2`}
                  onSubmit={(event) => {
                    event.preventDefault();
                    if (!reason.trim()) {
                      // 集約も断るが、押してから 400 で気づく形にしない。
                      setReasonError('キャンセル理由を入力してください');
                      return;
                    }
                    setReasonError('');
                    cancel.mutate();
                  }}
                >
                  <p className="text-sm">
                    キャンセルすると、この航海は経路候補に出なくなり、スケジュールも直せなくなります。
                  </p>
                  <label htmlFor="cancel-reason" className={LABEL}>
                    キャンセル理由
                  </label>
                  <input
                    id="cancel-reason"
                    className={FIELD}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                  />
                  {reasonError && (
                    <p role="alert" className={ALERT}>
                      {reasonError}
                    </p>
                  )}
                  {cancel.isError && (
                    <p role="alert" className={ALERT}>
                      キャンセルできませんでした
                    </p>
                  )}
                  <div className="flex gap-2">
                    <button
                      type="submit"
                      className={BUTTON_DANGER}
                      disabled={cancel.isPending}
                    >
                      キャンセルを確定する
                    </button>
                    <button
                      type="button"
                      className={BUTTON_SECONDARY}
                      onClick={() => {
                        setCancelling(false);
                        setReasonError('');
                      }}
                    >
                      やめる
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          <p className="mt-4 flex gap-4 text-sm">
            {/* 判定は書き直さず述語を呼ぶ（不変条件 5）。 */}
            {canUpdateSchedule(data.value) && (
              <Link to={`/voyages/${encodeURIComponent(voyageNumber)}/edit`} className={LINK}>
                更新する
              </Link>
            )}
            <Link to="/voyages" className={LINK}>
              航海スケジュール一覧へ
            </Link>
          </p>
        </>
      )}
    </section>
  );
}

/** キャンセルの日時と実行者。止めた記録が無ければ何も出さない。 */
function cancelledStamp(voyage: VoyageView): string {
  if (!voyage.cancelledAt) {
    return '';
  }
  const by = voyage.cancelledBy ? ` / ${voyage.cancelledBy}` : '';
  return `（${formatVoyageTime(voyage.cancelledAt)}${by}）`;
}
