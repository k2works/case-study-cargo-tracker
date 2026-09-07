import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
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
import { formatBusinessDateTime } from '@/shared/api/businessDate';
import {
  fetchPublicTracking,
  isWellFormedTrackingNumber,
  normalizeTrackingNumber,
  type PublicTrackingView,
} from './api';

/** 問い合わせ窓口（ui_design.md「問い合わせの出口」）。 */
const SUPPORT = 'support@example.com / 03-xxxx-xxxx';

/** 30 秒ごとに更新する（ui_design.md）。荷受人は画面を開いたまま待つ。 */
const REFETCH_INTERVAL_MS = 30_000;

/**
 * 公開追跡照会（`/track/:trackingNumber`）。**認証不要**。
 *
 * <p>荷受人が追跡番号だけで問い合わせる入口。ロール別の到達性は認証済みの利用者に
 * しか働かないので、認証の外にも入口が要る（ui_design.md）。</p>
 *
 * <p><b>見つからないときに、存在しない番号と権限の無い番号を区別しない。</b>
 * 区別すると、総当たりで「実在するが自分のものではない番号」を選り分けられる。
 * サーバも 404 しか返さない。</p>
 *
 * <p><b>公開画面には例外の詳細・荷主名・金額を出さない。</b> サーバが渡さないので
 * ここで間違えても漏れないが、画面としても出さないことを検査で固定する。</p>
 */
export function PublicTrackingPage() {
  const { trackingNumber } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(trackingNumber ?? '');
  const [formatError, setFormatError] = useState<string | null>(null);

  const tracking = useQuery({
    queryKey: ['public-tracking', trackingNumber],
    queryFn: () => fetchPublicTracking(trackingNumber ?? ''),
    enabled: trackingNumber !== undefined,
    retry: false,
    refetchInterval: REFETCH_INTERVAL_MS,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    // **照会前に形を見る。** 送ってしまうと、レート制限（429）の回数を無駄に使い、
    // 正しい番号を打ち直すころには断られる。
    if (!isWellFormedTrackingNumber(input)) {
      setFormatError('追跡番号の形式が違います。');
      return;
    }
    setFormatError(null);
    navigate(`/track/${normalizeTrackingNumber(input)}`);
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <main className="mx-auto max-w-2xl p-8">
        <h1 className={PAGE_TITLE}>荷物の追跡</h1>
        <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

        <form onSubmit={submit} className={`${CARD} mt-6`}>
          <label htmlFor="trackingNumber" className={LABEL}>
            追跡番号
          </label>
          <input
            id="trackingNumber"
            className={FIELD}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="TRK-AB12CD3456"
          />
          <button type="submit" className={`${BUTTON_PRIMARY} mt-4`}>
            照会
          </button>
          {formatError !== null && (
            <output className={`${ALERT} mt-4`}>
              {formatError}
              <br />
              {FORMAT_HINT}
            </output>
          )}
        </form>

        {tracking.isFetching && tracking.data === undefined && (
          <p className="mt-6 text-sm text-gray-600">照会しています…</p>
        )}
        {tracking.isError && <Unavailable error={tracking.error} />}
        {tracking.data?.state === 'ready' && <Result tracking={tracking.data.value} />}

        <p className="mt-8 text-sm text-gray-600">
          到着が遅れる場合のお問い合わせ: {SUPPORT}
          （予約番号または追跡番号をお伝えください）
        </p>
        <p className="mt-6 text-sm text-gray-600">
          <Link to="/login" className={LINK}>
            ログイン画面へ戻る
          </Link>
        </p>
      </main>
    </div>
  );
}

/** 入力形式のヒント。見つからないときと形式違いの両方で出す。 */
const FORMAT_HINT =
  '追跡番号は「TRK-」で始まる英数字 10 桁です（例: TRK-AB12CD3456）。'
  + '予約確定の案内に記載されています。';

/**
 * 照会できなかった。<b>理由で書き分ける</b>。
 *
 * <p>「見つからない」と「連打を断られた」は、荷受人が次に取る行動が違う。
 * 前者は番号を確かめ直し、後者は待つ。同じ文言にすると、待てば見られる人が
 * 番号を疑い続ける。</p>
 */
function Unavailable({ error }: { readonly error: unknown }) {
  if (error instanceof ApiError && error.status === 429) {
    return (
      <output className={`${NOTICE} mt-6`}>
        照会が続いています。しばらく待ってから再度お試しください。
      </output>
    );
  }
  return (
    <output className={`${ALERT} mt-6`}>
      追跡番号が見つかりません。
      <br />
      {FORMAT_HINT}
      <br />
      見つからない場合は出荷元の担当者、またはお問い合わせ窓口（{SUPPORT}）にご連絡ください。
    </output>
  );
}

/** 照会できた中身（S44）。 */
function Result({ tracking }: { readonly tracking: PublicTrackingView }) {
  return (
    <section className={`${CARD} mt-6`}>
      <h2 className={SECTION_TITLE}>
        <span className="font-mono">{tracking.trackingNumber}</span>{' '}
        <span className="rounded bg-blue-50 px-2 py-1 text-sm text-blue-800">
          {tracking.statusLabel}
        </span>
      </h2>

      <dl className="mt-4 grid grid-cols-[6rem_1fr] gap-y-2 text-sm">
        <dt className="text-gray-600">出発</dt>
        <dd className="text-gray-900">
          {tracking.originUnLocode}
          {tracking.departedAt !== null && `\u3000${formatBusinessDateTime(tracking.departedAt)}`}
        </dd>
        <dt className="text-gray-600">到着予定</dt>
        <dd className="text-gray-900">
          {tracking.destinationUnLocode}
          {tracking.estimatedArrival !== null
            && `\u3000${formatBusinessDateTime(tracking.estimatedArrival)}`}
        </dd>
        {tracking.currentUnLocode !== null && (
          <>
            <dt className="text-gray-600">現在</dt>
            <dd className="text-gray-900">{tracking.currentUnLocode}</dd>
          </>
        )}
      </dl>

      {tracking.history.length > 0 && (
        <div className="mt-6 overflow-x-auto">
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>輸送の記録</caption>
            <thead>
              <tr>
                <th className={TH}>日時</th>
                <th className={TH}>出来事</th>
                <th className={TH}>場所</th>
              </tr>
            </thead>
            <tbody>
              {tracking.history.map((event) => (
                <tr key={`${event.occurredAt}-${event.statusLabel}`}>
                  <td className={TD}>{formatBusinessDateTime(event.occurredAt)}</td>
                  <td className={TD}>{event.statusLabel}</td>
                  <td className={TD}>{event.location ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="mt-4 text-sm text-gray-600">30 秒ごとに更新します。</p>
    </section>
  );
}
