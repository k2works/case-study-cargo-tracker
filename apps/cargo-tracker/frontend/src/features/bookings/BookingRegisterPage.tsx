import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { display, fetchShippers } from '@/features/shippers/api';
import { ALERT, BUTTON_PRIMARY, CARD, FIELD, LABEL, LINK, PAGE_TITLE } from '@/shared/ui/styles';
import { bookCargo, type CargoType } from './api';
import { CargoFields, cargoFieldsPayload } from './CargoFields';

/**
 * S21 予約登録（UC03 / US04）。
 *
 * <p>種別ごとの入力欄は<b>その種別を選んだときだけ</b>出す。常に出すと
 * 「一般貨物なのに危険物申告を求められる」ことになる（ui_design.md S11 と同じ考え）。</p>
 *
 * <p>見積の欄は出さない。見積（US01）が未実装のうちは、選べない欄を置いても
 * 「使えない機能がある」ようにしか見えない。</p>
 */
/** 荷主は選ぶ（S21）。入力欄が無い修正画面と共通化しないのはこの 1 項目だけ。 */
function shipperIdOf(form: FormData): string {
  const value = form.get('shipperId');
  return typeof value === 'string' ? value : '';
}

export function BookingRegisterPage() {
  const [cargoType, setCargoType] = useState<CargoType>('GENERAL');
  // 荷主は選ぶ（UI 設計 S21）。識別子を打たせると、営業は一覧を開いて
  // UUID を書き写すことになる。荷主コードは画面に出ているが、予約が要るのは
  // 識別子なので、対応づけを人にやらせない。
  const { data: shippers } = useQuery({ queryKey: ['shippers'], queryFn: fetchShippers });
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
      await bookCargo({
        shipperId: shipperIdOf(form),
        ...cargoFieldsPayload(form, cargoType),
      });
      // 受け付けただけで一覧にはまだ出ない。一覧側が取り直せるようにしてから移る。
      await queryClient.invalidateQueries({ queryKey: ['bookings'] });
      navigate('/bookings', { state: { justBooked: true } });
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
          <label htmlFor="shipperId" className={LABEL}>
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

        <CargoFields cargoType={cargoType} onCargoTypeChange={setCargoType} />

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
