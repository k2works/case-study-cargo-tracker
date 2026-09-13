import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import { fetchDeadLetters, retryDeadLetters } from './deadLetterApi';

/** 経路の接頭辞からサービス名を出す（`/booking/dead-letters` → `booking`）。 */
function serviceOf(source: string): string {
  return source.split('/')[1] ?? source;
}

/**
 * S91 退避したイベント（ADR-0014 / IT15 引き継ぎ 1）。
 *
 * <p><b>「気づく手段」を画面に置く。</b> 退避先には運用の gulp タスクと Actuator
 * から届くが、どちらも端末からしか触れない。投影が止まっていることに気づけるのが
 * 端末を持つ人だけだと、気づくのはたいてい業務側から言われたあとになる。</p>
 *
 * <p><b>中身（payload）は出さない。</b> 退避されたイベントには個人情報が載りうる
 * （ADR-0003）。サーバも返さない。</p>
 *
 * <p><b>消す手段は置かない</b>（ADR-0014 決定 1）。直したあとに退避を消すのは
 * 「黙って捨てる」ことである。</p>
 */
export function DeadLetterListPage() {
  const client = useQueryClient();
  const { data, isPending, isError } = useQuery({
    queryKey: ['dead-letters'],
    queryFn: fetchDeadLetters,
  });

  const retry = useMutation({
    mutationFn: retryDeadLetters,
    onSuccess: () => client.invalidateQueries({ queryKey: ['dead-letters'] }),
  });

  const items = data?.state === 'ready' ? data.value.items : [];
  // **処理し直す宛先はサービスごと。** 束ねた一覧から 1 つの口に送ると、
  // 止まっていないサービスまで回すことになる。
  const services = [...new Set(items.map((item) => item.source))];

  return (
    <section>
      <h1 className={PAGE_TITLE}>退避したイベント</h1>
      <p className="mt-1 text-sm text-gray-600">
        投影や連鎖が書けなかったイベントです。<b>1 件で全部が止まらないように</b>
        退避先へ逃がしています。<b>原因を直してから処理し直してください</b>——
        直っていなければ、また退避されます。
      </p>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {data?.state === 'pending' && (
        <output className={`${NOTICE} mt-4 block`}>{data.message}</output>
      )}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          退避したイベントを取得できませんでした
        </p>
      )}
      {retry.isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          処理し直せませんでした
        </p>
      )}

      {data?.state === 'ready' && items.length === 0 && (
        <p className="mt-4 text-sm text-gray-600">退避しているイベントはありません。</p>
      )}

      {data?.state === 'ready' && items.length > 0 && (
        <>
          <div className="mt-4 flex flex-wrap gap-2">
            {services.map((source) => (
              <button
                key={source}
                className={BUTTON_PRIMARY}
                type="button"
                disabled={retry.isPending}
                onClick={() => retry.mutate(source)}
              >
                {serviceOf(source)} を処理し直す
              </button>
            ))}
            {retry.isPending && <output className={NOTICE}>送信中…</output>}
          </div>

          <div className={`${CARD} mt-4 overflow-x-auto`}>
            <table className={TABLE}>
              <caption className={TABLE_CAPTION}>
                退避しているイベント（{items.length} 件・新しい順）
              </caption>
              <thead>
                <tr>
                  <th scope="col" className={TH}>サービス</th>
                  <th scope="col" className={TH}>処理</th>
                  <th scope="col" className={TH}>イベント</th>
                  <th scope="col" className={TH}>列</th>
                  <th scope="col" className={TH}>止まった理由</th>
                  <th scope="col" className={TH}>退避</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={`${item.source}:${item.deadLetterId}`}>
                    <td className={TD}>{serviceOf(item.source)}</td>
                    <td className={TD}>{item.processingGroup}</td>
                    <td className={TD}>{item.eventType}</td>
                    <td className={TD}>{item.sequenceIdentifier}</td>
                    <td className={TD}>{item.causeMessage ?? item.causeType ?? '—'}</td>
                    <td className={TD}>
                      {item.enqueuedAt ? formatBusinessDateTime(item.enqueuedAt) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
