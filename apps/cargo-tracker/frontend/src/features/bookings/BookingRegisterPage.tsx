import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { display, fetchShippers } from '@/features/shippers/api';
import { ALERT, BUTTON_PRIMARY, CARD, FIELD, LABEL, LINK, PAGE_TITLE } from '@/shared/ui/styles';
import { fetchQuotation } from '@/features/quotations/api';
import { bookCargo, type CargoType } from './api';
import { CargoFields, cargoFieldsPayload } from './CargoFields';

/**
 * S21 予約登録（UC03 / US04）。
 *
 * <p>種別ごとの入力欄は<b>その種別を選んだときだけ</b>出す。常に出すと
 * 「一般貨物なのに危険物申告を求められる」ことになる（ui_design.md S11 と同じ考え）。</p>
 *
 * <p><b>見積から来たときは 5 項目を写す</b>（US01・S13 の `[この見積で予約する]`）。
 * 写さないと、営業担当者は見積を開き直して打ち直すことになり、写し間違いが
 * 見積と違う予約を黙って作る。</p>
 *
 * <p><b>見積から来ていなければ、見積の欄は出しません。</b> 選べない欄を置くと
 * 「使えない機能がある」ようにしか見えません。</p>
 *
 * <p><b>写した項目は変えられます。</b> 荷主の事情は見積のあとで変わるので、
 * 固定すると業務が止まります。違いはサーバが項目名で知らせます
 * （正典の不変条件 3）。</p>
 */
/** 荷主は選ぶ（S21）。入力欄が無い修正画面と共通化しないのはこの 1 項目だけ。 */
function shipperIdOf(form: FormData): string {
  const value = form.get('shipperId');
  return typeof value === 'string' ? value : '';
}

export function BookingRegisterPage() {
  const [searchParams] = useSearchParams();
  const quotationId = searchParams.get('quotationId');

  // **見積から来たときだけ読む。** 見積を経ない予約のほうが多いので、
  // 常に問い合わせると無駄な往復が増える。
  const { data: quotation } = useQuery({
    queryKey: ['quotation', quotationId],
    queryFn: () => fetchQuotation(String(quotationId)),
    enabled: quotationId !== null,
  });
  const quoted = quotation?.state === 'ready' ? quotation.value : null;

  const [cargoType, setCargoType] = useState<CargoType>('GENERAL');
  // 荷主は選ぶ（UI 設計 S21）。識別子を打たせると、営業は一覧を開いて
  // UUID を書き写すことになる。荷主コードは画面に出ているが、予約が要るのは
  // 識別子なので、対応づけを人にやらせない。
  // **選択肢は一覧と同じ上限で作られる。** 絞り込めないと、新しく登録した荷主で
  // その日から予約が取れない（IT8 のクラスタで実測）。
  const [shipperQuery, setShipperQuery] = useState('');
  const { data: shippers } = useQuery({
    queryKey: ['shippers', shipperQuery],
    queryFn: () => fetchShippers(shipperQuery),
  });
  // **貨物種別も写す。** 写さないと、冷凍の見積が一般貨物の予約になる。
  // 入力欄の既定値（defaultValue）とは別に、種別だけは state が持っている。
  const [quotedTypeApplied, setQuotedTypeApplied] = useState(false);
  if (quoted !== null && !quotedTypeApplied) {
    setQuotedTypeApplied(true);
    setCargoType(quoted.cargoType);
  }

  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function onSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    try {
      const booked = await bookCargo({
        shipperId: shipperIdOf(form),
        ...cargoFieldsPayload(form, cargoType),
        // **見積番号を一緒に送る。** 送らないと、サーバは見積と突き合わせられず
        // 「見積と異なる項目」を知らせられない（正典の不変条件 3）。
        ...(quotationId === null ? {} : { quotationId }),
      });
      // 受け付けただけで一覧にはまだ出ない。一覧側が取り直せるようにしてから移る。
      await queryClient.invalidateQueries({ queryKey: ['bookings'] });
      // **見積と違った項目を持って行く。** 受け取って捨てると、断らずに
      // 知らせるという約束が画面の手前で消える（正典の不変条件 3）。
      navigate('/bookings', {
        state: { justBooked: true, quotationDifferences: booked.quotationDifferences ?? [] },
      });
    } catch (e) {
      // 断ったのは集約の判断であって画面の誤りではない。理由をそのまま見せる。
      setError(
        e instanceof ApiError ? e.body.message : '登録できませんでした。もう一度お試しください',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h1 className={PAGE_TITLE}>貨物予約の登録</h1>

      <form onSubmit={onSubmit} className={`${CARD} mt-4 space-y-4`}>
        <div>
          <label htmlFor="shipperQuery" className={LABEL}>
            荷主を名前で絞り込む
          </label>
          <input
            id="shipperQuery"
            className={FIELD}
            value={shipperQuery}
            onChange={(event) => setShipperQuery(event.target.value)}
            placeholder="山田"
          />

          <label htmlFor="shipperId" className={`${LABEL} mt-4`}>
            荷主
          </label>
          <select id="shipperId" name="shipperId" required className={FIELD}>
            <option value="">選んでください</option>
            {shippers?.state === 'ready'
              && shippers.value.items.map((shipper) => (
                <option key={shipper.shipperId} value={shipper.shipperId}>
                  {display(shipper.name)}（{shipper.shipperCode}）
                </option>
              ))}
          </select>
          {/* 荷主が 1 件も無いときは、空の選択肢だけを出さずに理由を言う。
              初日や新しい拠点では必ずこの状態から始まる。 */}
          {shippers?.state === 'ready' && shippers.value.items.length === 0 && (
            <p className="mt-1 text-sm text-gray-600">
              登録されている荷主がありません。先に
              <Link to="/shippers/new" className={LINK}>
                荷主を登録
              </Link>
              してください。
            </p>
          )}
          {shippers === undefined && (
            <p className="mt-1 text-sm text-gray-600">荷主を読み込んでいます…</p>
          )}
          {shippers?.state === 'ready'
            && shippers.value.total > shippers.value.items.length && (
              <p className="mt-1 text-sm text-gray-600">
                荷主は {shippers.value.total} 件のうち {shippers.value.items.length} 件を
                表示しています
              </p>
            )}
        </div>

        {/* **見積から来たときだけ出す。** 選べない欄を置くと「使えない機能が
            ある」ようにしか見えない（ui_design.md S21）。 */}
        {quoted !== null && (
          <p className="rounded border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900">
            見積 {quoted.quotationId} の内容を写しました。変更すると、登録後に
            「見積と異なる項目」としてお知らせします。
          </p>
        )}

        <CargoFields
          cargoType={cargoType}
          onCargoTypeChange={setCargoType}
          defaults={quoted === null ? undefined : {
            originUnLocode: quoted.originUnLocode,
            destinationUnLocode: quoted.destinationUnLocode,
            arrivalDeadline: quoted.arrivalDeadline,
            weightKg: String(quoted.weightKg),
          }}
        />

        {error !== null && (
          <p role="alert" className={ALERT}>
            {error}
          </p>
        )}

        {/* 送信中は disabled でなく aria-disabled にしてフォーカスを保つ。 */}
        <button type="submit" aria-disabled={submitting} className={BUTTON_PRIMARY}>
          {submitting ? '登録中…' : '登録する'}
        </button>
      </form>
    </section>
  );
}
