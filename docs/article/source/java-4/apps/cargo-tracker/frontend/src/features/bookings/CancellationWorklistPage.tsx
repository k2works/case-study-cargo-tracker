import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import {
  ALERT,
  BUTTON_DANGER,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { ApiError } from '@/shared/api/client';
import {
  approveCancellation,
  fetchDischargeCandidates,
  fetchPendingCancellations,
  rejectCancellation,
  type CancellationRequestView,
} from './cancellationApi';

/** 承認待ちは 5 秒ごとに取り直す（申請は他の人が出す）。 */
const REFETCH_INTERVAL_MS = 5000;

/** 判断が記録できなかった理由。**サーバの断り文をそのまま出す**（原因が読める）。 */
function decisionError(error: unknown): string {
  return error instanceof ApiError ? error.message : '判断を記録できませんでした';
}

/**
 * 判断の欄（1 件ぶん）。
 *
 * <p><b>陸揚げ地の選択肢はサーバから取る。</b> 集約が断る条件と同じ関数から
 * 作られるので、出ているのに押すと断られる港が生まれない。</p>
 */
function DecisionForm({ request, onDone }: Readonly<{
  request: CancellationRequestView;
  onDone: () => void;
}>) {
  const [dischargeUnLocode, setDischargeUnLocode] = useState('');
  const [reason, setReason] = useState('');

  const candidates = useQuery({
    queryKey: ['discharge-candidates', request.bookingId],
    queryFn: () => fetchDischargeCandidates(request.bookingId),
  });

  const approve = useMutation({
    mutationFn: () => approveCancellation(request.bookingId, dischargeUnLocode, reason),
    onSuccess: onDone,
  });
  const reject = useMutation({
    mutationFn: () => rejectCancellation(request.bookingId, reason),
    onSuccess: onDone,
  });

  const ports = candidates.data?.state === 'ready' ? candidates.data.value.unLocodes : [];
  const current = candidates.data?.state === 'ready'
    ? candidates.data.value.currentUnLocode : null;
  // **押せない理由を出す。** 選択肢が取れないと `[承認する]` は押せないが、
  // なぜ押せないのかが読めないと、判断する人は待つことしかできない
  // （IT15 のレビュー 中。一覧側は出し分けているのに判断欄だけ落ちていた）。
  const candidatesPending = candidates.isPending
    || candidates.data?.state === 'pending';

  return (
    <div className={`${CARD} mt-3`}>
      <h3 className="text-base font-semibold text-gray-900">
        {request.bookingNumber ?? request.bookingId} の判断
      </h3>
      <p className="mt-1 text-sm text-gray-600">
        申請: {request.reason}（{request.requestedBy}・
        {formatBusinessDateTime(request.requestedAt)}）
      </p>

      <div className="mt-3 flex flex-wrap items-end gap-4">
        <div>
          <label className={LABEL} htmlFor={`discharge-${request.requestId}`}>陸揚げ地</label>
          <select
            id={`discharge-${request.requestId}`}
            className={FIELD}
            value={dischargeUnLocode}
            onChange={(event) => setDischargeUnLocode(event.target.value)}
          >
            <option value="">選んでください</option>
            {ports.map((port) => (
              <option key={port} value={port}>
                {port}{port === current ? '（現在地）' : ''}
              </option>
            ))}
          </select>
          {/* **どこなら指定できるかを出す。** 0 件の候補から選ばせない。 */}
          <p className="mt-1 text-xs text-gray-600">
            現在地または残りの寄港地から選びます。
          </p>
          {candidatesPending && <output className={NOTICE}>選択肢を読み込み中…</output>}
          {candidates.isError && (
            <p role="alert" className={`${ALERT} mt-1`}>
              陸揚げ地の選択肢を取得できませんでした。承認はできません（却下はできます）。
            </p>
          )}
        </div>
        <div className="grow">
          <label className={LABEL} htmlFor={`reason-${request.requestId}`}>理由</label>
          <input
            id={`reason-${request.requestId}`}
            className={FIELD}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="荷主の指定倉庫が近い / 荷受人がすでに手配済み"
          />
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          className={BUTTON_PRIMARY}
          type="button"
          disabled={approve.isPending || dischargeUnLocode === ''}
          onClick={() => approve.mutate()}
        >
          承認する
        </button>
        {/* **却下には理由が要る。** 申請した営業が次の手を決められない。 */}
        <button
          className={BUTTON_DANGER}
          type="button"
          disabled={reject.isPending || reason.trim() === ''}
          onClick={() => reject.mutate()}
        >
          却下する
        </button>
        <Link to={`/bookings/${request.bookingId}`} className={LINK}>
          予約を見る
        </Link>
        {(approve.isPending || reject.isPending) && (
          <output className={NOTICE}>送信中…</output>
        )}
      </div>

      {(approve.isError || reject.isError) && (
        <p role="alert" className={`${ALERT} mt-3`}>
          {decisionError(approve.error ?? reject.error)}
        </p>
      )}
    </div>
  );
}

/**
 * S23 キャンセル承認一覧（UC22 / US30 §受入基準 4・5・7）。
 *
 * <p><b>宛先は追跡管理者。</b> 陸揚げ地を決められるのはその人だけで、営業には
 * 打つ手が無い——気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。</p>
 *
 * <p><b>申請日時が古い順。</b> 待たせているものから答える。</p>
 */
export function CancellationWorklistPage() {
  const client = useQueryClient();
  const [deciding, setDeciding] = useState<string | null>(null);

  const { data, isPending, isError } = useQuery({
    queryKey: ['pending-cancellations'],
    queryFn: fetchPendingCancellations,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  const items = data?.state === 'ready' ? data.value.items : [];

  function afterDecision() {
    setDeciding(null);
    void client.invalidateQueries({ queryKey: ['pending-cancellations'] });
  }

  return (
    <section>
      <h1 className={PAGE_TITLE}>キャンセル承認</h1>
      <p className="mt-1 text-sm text-gray-600">
        輸送中の予約に対するキャンセルの申請です。{' '}
        <b>承認するには陸揚げ地を決めます</b>——決めずに承認しても、貨物は船の上に
        残ります。<b>承認しても追跡は閉じません</b>。その港で荷降しを記録してから
        閉じます。
      </p>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4 block`}>{data.message}</output>
      )}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          キャンセル申請を取得できませんでした
        </p>
      )}

      {data?.state === 'ready' && items.length === 0 && (
        <p className="mt-4 text-sm text-gray-600">承認待ちのキャンセル申請はありません。</p>
      )}

      {items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>
              承認待ちのキャンセル申請（{items.length} 件・申請の古い順）
            </caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>予約</th>
                <th scope="col" className={TH}>品名</th>
                <th scope="col" className={TH}>理由</th>
                <th scope="col" className={TH}>申請者</th>
                <th scope="col" className={TH}>申請</th>
                <th scope="col" className={TH}>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.requestId} data-testid={`cancellation-${item.bookingId}`}>
                  <td className={TD}>
                    <Link to={`/bookings/${item.bookingId}`} className={LINK}>
                      {item.bookingNumber ?? item.bookingId}
                    </Link>
                  </td>
                  <td className={TD}>{item.productName ?? '—'}</td>
                  <td className={TD}>{item.reason}</td>
                  <td className={TD}>{item.requestedBy}</td>
                  <td className={TD}>{formatBusinessDateTime(item.requestedAt)}</td>
                  <td className={TD}>
                    <button
                      className={BUTTON_PRIMARY}
                      type="button"
                      onClick={() => setDeciding(item.requestId)}
                    >
                      判断する
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {items
        .filter((item) => item.requestId === deciding)
        .map((item) => (
          <DecisionForm key={item.requestId} request={item} onDone={afterDecision} />
        ))}
    </section>
  );
}
