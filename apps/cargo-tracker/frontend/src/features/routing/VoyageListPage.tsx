import { useState, type SubmitEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation, useSearchParams } from 'react-router';
import { ApiError } from '@/shared/api/client';
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
import {
  acceptedCargoTypeLabel,
  departurePeriod,
  fetchVoyages,
  formatVoyageTime,
  type VoyageSearchInput,
} from './api';

const CARGO_TYPES = ['GENERAL', 'HAZARDOUS', 'REEFER'] as const;

/** 入力中の条件。空文字は「指定なし」で、解釈はサーバが正典（US07）。 */
interface SearchForm {
  departure: string;
  arrival: string;
  departFrom: string;
  departTo: string;
  cargoType: string;
}

const EMPTY_FORM: SearchForm = {
  departure: '',
  arrival: '',
  departFrom: '',
  departTo: '',
  cargoType: '',
};

function toCriteria(form: SearchForm): VoyageSearchInput {
  return {
    departure: form.departure.trim(),
    arrival: form.arrival.trim(),
    cargoType: form.cargoType,
    ...departurePeriod(form.departFrom, form.departTo),
  };
}

/**
 * S32 航海スケジュール一覧（UC19）。
 *
 * <p>既定では出港済みとキャンセルを外し、出発日が近い順に並べる（ui_design.md）。
 * 出港してしまった便が混ざると、一覧全体が「これから使える航海」として
 * 信用されなくなる。</p>
 *
 * <p>検索条件（出発地・目的地・出発期間・対応貨物種別）はこの画面が持つ（US07）。
 * 条件の解釈はサーバの VoyageSearchCriteria が正典で、ここでは組み立てるだけ。</p>
 *
 * <p>S30 から「対応する航海を探す」で来たときは、予約の条件をクエリ文字列で
 * 受け取って初期値にする。</p>
 */

/**
 * 一覧に出す状態。キャンセルが最優先で、次に出港したかどうかを見る。
 *
 * <p>「出港済み・キャンセルも表示」を選んだときに混ざるのはこの 2 つなので、
 * 「予定」しか出さないと、何が混ざったのかが分からない。</p>
 */
function voyageStateLabel(voyage: { cancelled: boolean; departureAt: string }): string {
  if (voyage.cancelled) {
    return 'キャンセル';
  }
  return new Date(voyage.departureAt).getTime() < Date.now() ? '出港済み' : '予定';
}

export function VoyageListPage() {
  const [includeFinished, setIncludeFinished] = useState(false);
  const justRegistered =
    (useLocation().state as { justRegistered?: boolean } | null)?.justRegistered === true;
  // S30 から「対応する航海を探す」で来たときは貨物種別を引き継ぐ。
  // 引き継がないと、危険物の予約を見ていた経路設計者が、ここで種別を
  // 選び直すことになり、選び忘れれば対応しない航海まで候補に見える。
  const [params] = useSearchParams();
  const initial: SearchForm = {
    departure: params.get('departure') ?? '',
    arrival: params.get('arrival') ?? '',
    departFrom: params.get('departFrom') ?? '',
    departTo: params.get('departTo') ?? '',
    cargoType: params.get('cargoType') ?? '',
  };
  const [form, setForm] = useState<SearchForm>(initial);
  // 「絞り込む」を押したときの条件。入力のたびに問い合わせると、
  // 打っている途中の港で 0 件が出て「無い」と読めてしまう。
  const [applied, setApplied] = useState<SearchForm>(initial);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['voyages', includeFinished, applied],
    queryFn: () => fetchVoyages(includeFinished, toCriteria(applied)),
    // 投影は非同期なので、登録直後は数秒ぶん遅れる。定期に取り直す。
    refetchInterval: 3000,
    retry: false,
  });

  const filtered = Object.values(applied).some((value) => value !== '');

  function search(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setApplied(form);
  }

  function clearSearch() {
    setForm(EMPTY_FORM);
    setApplied(EMPTY_FORM);
  }

  return (
    <section>
      <h1 className={PAGE_TITLE}>航海スケジュール一覧</h1>
      <p className="mt-2 text-sm">
        <Link to="/voyages/new" className={LINK}>
          航海を登録する
        </Link>
      </p>

      <label className="mt-3 flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={includeFinished}
          onChange={(event) => setIncludeFinished(event.target.checked)}
        />
        {'出港済み・キャンセルも表示'}
      </label>

      {justRegistered && (
        <output className={`${NOTICE} mt-4 block`}>
          登録を受け付けました。反映までしばらくお待ちください
        </output>
      )}

      {/* 条件で絞る（US07）。港湾制約と経路探索は US08。 */}
      <form onSubmit={search} className={`${CARD} mt-4 space-y-3`}>
        <h2 className={SECTION_TITLE}>条件で絞り込む</h2>
        {/* 端点だけで絞ることを言う。途中の寄港地で探して 0 件が出ると、
            「その港へ行く便が無い」と読まれる（経路の探索は US08）。 */}
        <p className="text-sm text-gray-600">
          始発港と最終港で絞り込みます。途中の寄港地では絞り込めません。
          出発日は日本時間で判定します。
        </p>
        <div className="grid gap-3 sm:grid-cols-3">
          <label className={LABEL}>
            <span>出発地</span>
            <input
              className={FIELD}
              placeholder="JPTYO"
              value={form.departure}
              onChange={(event) => setForm({ ...form, departure: event.target.value })}
            />
          </label>
          <label className={LABEL}>
            <span>目的地</span>
            <input
              className={FIELD}
              placeholder="USNYC"
              value={form.arrival}
              onChange={(event) => setForm({ ...form, arrival: event.target.value })}
            />
          </label>
          <label className={LABEL}>
            <span>対応貨物種別</span>
            <select
              className={FIELD}
              value={form.cargoType}
              onChange={(event) => setForm({ ...form, cargoType: event.target.value })}
            >
              <option value="">指定なし</option>
              {CARGO_TYPES.map((cargoType) => (
                <option key={cargoType} value={cargoType}>
                  {acceptedCargoTypeLabel(cargoType)}
                </option>
              ))}
            </select>
          </label>
          <label className={LABEL}>
            <span>出発日（開始）</span>
            <input
              className={FIELD}
              type="date"
              value={form.departFrom}
              onChange={(event) => setForm({ ...form, departFrom: event.target.value })}
            />
          </label>
          <label className={LABEL}>
            <span>出発日（終了）</span>
            <input
              className={FIELD}
              type="date"
              value={form.departTo}
              onChange={(event) => setForm({ ...form, departTo: event.target.value })}
            />
          </label>
        </div>
        <div className="flex gap-3">
          <button type="submit" className={BUTTON_PRIMARY}>
            絞り込む
          </button>
          {filtered && (
            <button type="button" className="text-sm text-blue-700 underline" onClick={clearSearch}>
              条件を消して探し直す
            </button>
          )}
        </div>
      </form>

      {isPending && <output className={`${NOTICE} mt-4`}>読み込み中…</output>}
      {/* 入力の誤りは 0 件に見せない。0 件は「その条件の航海が無い」と読める。 */}
      {isError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          {error instanceof ApiError ? error.body.message : '一覧を取得できませんでした'}
        </p>
      )}
      {data?.state === 'pending' && <output className={`${NOTICE} mt-4`}>{data.message}</output>}

      {/* 見出しだけの表を出すと「読み込みに失敗した」と受け取られる。 */}
      {data?.state === 'ready' && data.value.items.length === 0 && (
        <output className={`${NOTICE} mt-4 block`}>
          {filtered ? '条件に合う航海はありません' : '航海はありません'}
        </output>
      )}

      {/* 上限で切れていることを黙らない。無音で切れると、載らなかった航海は
          誰の目にも入らないまま残る。 */}
      {data?.state === 'ready' && data.value.total > data.value.items.length && (
        <output className={`${NOTICE} mt-4 block`}>
          {/* 内部のストーリー ID は画面に出さない。利用者には意味が無く、
              「US07 とは何か」という問い合わせになる。 */}
          {data.value.total} 件のうち {data.value.items.length} 件を表示しています。
          条件で絞り込んでください
        </output>
      )}

      {data?.state === 'ready' && data.value.items.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>航海スケジュールの一覧</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>航海番号</th>
                <th scope="col" className={TH}>運送会社</th>
                <th scope="col" className={TH}>船名</th>
                <th scope="col" className={TH}>出発地 → 到着地</th>
                <th scope="col" className={TH}>出発</th>
                <th scope="col" className={TH}>到着</th>
                <th scope="col" className={TH}>対応貨物</th>
                <th scope="col" className={TH}>状態</th>
              </tr>
            </thead>
            <tbody>
              {data.value.items.map((item) => (
                <tr key={item.voyageNumber}>
                  <td className={TD}>
                    {/* 登録した中身を確認できる先を持たせる（IT3 レビュー）。 */}
                    <Link to={`/voyages/${encodeURIComponent(item.voyageNumber)}`} className={LINK}>
                      {item.voyageNumber}
                    </Link>
                  </td>
                  <td className={TD}>{item.carrierName}</td>
                  <td className={TD}>{item.vesselName}</td>
                  <td className={TD}>
                    {item.departureUnLocode} → {item.arrivalUnLocode}
                  </td>
                  <td className={TD}>{formatVoyageTime(item.departureAt)}</td>
                  <td className={TD}>{formatVoyageTime(item.arrivalAt)}</td>
                  <td className={TD}>
                    {item.acceptedCargoTypes.map(acceptedCargoTypeLabel).join(' / ')}
                  </td>
                  {/* 「出港済みも表示」にしても状態が「予定」のままだと、
                      混ざっているのに見分けられない。出港したかどうかは
                      最初の出港日時で決まる。 */}
                  <td className={TD}>{voyageStateLabel(item)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
