import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import {
  ALERT,
  CARD,
  LINK,
  NOTICE,
  PAGE_TITLE,
  TABLE,
  TABLE_CAPTION,
  TD,
  TH,
} from '@/shared/ui/styles';
import { formatMoney } from '@/shared/ui/money';
import { fetchQuotation } from './api';

/**
 * S13 見積詳細（US01 §受入基準 3・5）。
 *
 * <p><b>候補は「選べる案」として読めなければならない。</b> 経由港・所要日数・
 * 概算料金・航海番号の 4 つが揃って初めて、営業担当者は荷主に説明できる。</p>
 *
 * <p><b>間に合わない候補も出す。</b> 消すと「経路がありません」に見えるが、
 * 実際には「期限を延ばせば通せる案がある」——超過日数を添えて、その判断を
 * 荷主に返せるようにする。</p>
 */
export function QuotationDetailPage() {
  const { quotationId = '' } = useParams();
  const quotation = useQuery({
    queryKey: ['quotation', quotationId],
    queryFn: () => fetchQuotation(quotationId),
    // 受付から投影までは数秒。**待っているあいだも「反映中」と分かる。**
    refetchInterval: (query) => (query.state.data?.state === 'pending' ? 2000 : false),
  });

  if (quotation.isError) {
    return <p role="alert" className={ALERT}>見積を取得できませんでした</p>;
  }
  if (quotation.data?.state === 'pending') {
    return <output className={`${NOTICE} block`}>{quotation.data.message}</output>;
  }
  if (quotation.data?.state !== 'ready') {
    return <output className={`${NOTICE} block`}>読み込み中です</output>;
  }

  const view = quotation.data.value;

  return (
    <section>
      <h1 className={PAGE_TITLE}>見積 {view.quotationId}</h1>
      <p className="mt-1 text-sm text-gray-600">
        {view.originUnLocode} → {view.destinationUnLocode}
        {' / '}
        希望到着期限 {view.arrivalDeadline}
        {' / '}
        {view.weightKg.toLocaleString('ja-JP')} kg
        {' / '}
        有効期限 {view.validUntil}
      </p>

      <p className="mt-3 text-lg font-semibold text-gray-900">
        概算料金 {formatMoney(view.estimatedAmount, view.currency)}
      </p>

      {/* **「候補が無い」ことも答えである**（US01 §受入基準 5）。断って
          しまうと、営業担当者は荷主に何も返せない。 */}
      {!view.hasDeadlineMeetingCandidate && (
        <p
          role="status"
          className={
            'mt-3 block rounded border border-amber-300 bg-amber-50 px-4 py-3'
            + ' text-sm text-amber-900'
          }
        >
          希望期限に間に合う経路がありません。期限を延ばすか、条件を変えてご相談ください。
        </p>
      )}

      {view.candidates.length > 0 && (
        <div className={`${CARD} mt-4 overflow-x-auto`}>
          <table className={TABLE}>
            <caption className={TABLE_CAPTION}>ルート候補</caption>
            <thead>
              <tr>
                <th scope="col" className={TH}>航海番号</th>
                <th scope="col" className={TH}>経由港</th>
                <th scope="col" className={TH}>所要日数</th>
                <th scope="col" className={TH}>概算料金</th>
              </tr>
            </thead>
            <tbody>
              {view.candidates.map((candidate) => (
                <tr key={candidate.candidateSeq}>
                  <td className={`${TD} font-mono`}>{candidate.voyageNumbers}</td>
                  {/* **候補ごとの経由港を出す。** 出発地と目的地を繋ぐと、
                      どの候補も同じ経路に見えて案を選び分けられない。 */}
                  <td className={TD}>
                    {candidate.ports ?? `${view.originUnLocode} → ${view.destinationUnLocode}`}
                  </td>
                  <td className={`${TD} whitespace-nowrap`}>
                    {candidate.transitDays} 日
                    {/* **なぜ選べないかを添える。** 超過日数はサーバが数えたもの
                        を写す（画面で数え直すと、探索が使った期限とずれる）。 */}
                    {candidate.overdueDays > 0 && (
                      <span className="ml-2 text-amber-800">
                        （{candidate.overdueDays} 日超過）
                      </span>
                    )}
                  </td>
                  <td className={TD}>
                    {formatMoney(candidate.estimatedCost, candidate.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* **見積番号を引き継ぐ。** 引き継がないと、予約の側は見積と突き合わせ
          られず「見積と異なる項目」を知らせられない（正典の不変条件 3）。 */}
      <p className="mt-4">
        <Link
          className={LINK}
          to={`/bookings/new?quotationId=${encodeURIComponent(view.quotationId)}`}
        >
          この見積で予約する
        </Link>
      </p>
    </section>
  );
}
