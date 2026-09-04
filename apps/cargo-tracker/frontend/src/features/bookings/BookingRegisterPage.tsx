import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SubmitEvent } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { display, fetchShippers } from '@/features/shippers/api';
import { businessDate } from '@/shared/api/businessDate';
import { Link } from 'react-router';
import { ALERT, BUTTON_PRIMARY, CARD, FIELD, LABEL, LINK, PAGE_TITLE } from '@/shared/ui/styles';
import { bookCargo, type CargoType } from './api';

/**
 * S21 予約登録（UC03 / US04）。
 *
 * <p>種別ごとの入力欄は<b>その種別を選んだときだけ</b>出す。常に出すと
 * 「一般貨物なのに危険物申告を求められる」ことになる（ui_design.md S11 と同じ考え）。</p>
 *
 * <p>見積の欄は出さない。見積（US01）が未実装のうちは、選べない欄を置いても
 * 「使えない機能がある」ようにしか見えない。</p>
 */
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
    // FormData.get は File も返しうる。String() で包むだけだと
    // '[object Object]' が業務の値として送られる。文字列のときだけ採る。
    const text = (name: string) => {
      const value = form.get(name);
      return typeof value === 'string' ? value : '';
    };
    try {
      await bookCargo({
        shipperId: text('shipperId'),
        originUnLocode: text('originUnLocode').toUpperCase(),
        destinationUnLocode: text('destinationUnLocode').toUpperCase(),
        arrivalDeadline: text('arrivalDeadline'),
        cargoType,
        weightKg: text('weightKg'),
        // 寸法は集約が持つ値。画面が落とすと US04 §受入基準 2 を満たせない。
        lengthCm: text('lengthCm'),
        widthCm: text('widthCm'),
        heightCm: text('heightCm'),
        quantity: Number(text('quantity')),
        productName: text('productName'),
        hazardImoClass: cargoType === 'HAZARDOUS' ? text('hazardImoClass') : undefined,
        hazardUnNumber: cargoType === 'HAZARDOUS' ? text('hazardUnNumber') : undefined,
        temperatureMinC: cargoType === 'REFRIGERATED' ? text('temperatureMinC') : undefined,
        temperatureMaxC: cargoType === 'REFRIGERATED' ? text('temperatureMaxC') : undefined,
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

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="originUnLocode" label="出発地" required placeholder="JPTYO" />
          <Field id="destinationUnLocode" label="目的地" required placeholder="USNYC" />
        </div>

        <div>
          <label htmlFor="arrivalDeadline" className={LABEL}>
            到着期限
          </label>
          {/* 過去の日付を選べないようにする。年の打ち間違いは、経路設計者が
              「間に合う経路が 1 本も出ない」と気づくまで進んでしまう。
              業務タイムゾーンの今日を使う（toISOString() は UTC で 1 日ずれる）。 */}
          <input
            id="arrivalDeadline"
            name="arrivalDeadline"
            type="date"
            min={businessDate()}
            required
            className={FIELD}
          />
          <p className="mt-1 text-sm text-gray-600">当日に着く便は間に合う扱いです</p>
        </div>

        <fieldset>
          <legend className={LABEL}>貨物種別</legend>
          <div className="mt-1 flex gap-4">
            {(
              [
                ['GENERAL', '一般'],
                ['HAZARDOUS', '危険物'],
                ['REFRIGERATED', '冷凍・冷蔵'],
              ] as const
            ).map(([value, label]) => (
              <label key={value} className="flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  name="cargoType"
                  value={value}
                  checked={cargoType === value}
                  onChange={() => setCargoType(value)}
                  aria-label={label}
                />
                {label}
              </label>
            ))}
          </div>
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="weightKg" label="重量 (kg)" required inputMode="decimal" />
          <Field id="quantity" label="数量" required inputMode="numeric" />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <Field id="lengthCm" label="長さ (cm)" required inputMode="decimal" />
          <Field id="widthCm" label="幅 (cm)" required inputMode="decimal" />
          <Field id="heightCm" label="高さ (cm)" required inputMode="decimal" />
        </div>

        <Field id="productName" label="品名" required />

        {/* 危険物を選んだときだけ現れる。 */}
        {cargoType === 'HAZARDOUS' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="hazardImoClass" label="IMO クラス" required />
            <Field id="hazardUnNumber" label="UN 番号" required />
          </div>
        )}

        {/* 冷凍・冷蔵を選んだときだけ現れる。 */}
        {cargoType === 'REFRIGERATED' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="temperatureMinC" label="温度条件（下限 ℃）" required inputMode="decimal" />
            <Field id="temperatureMaxC" label="温度条件（上限 ℃）" required inputMode="decimal" />
          </div>
        )}

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

function Field({
  id,
  label,
  required,
  placeholder,
  inputMode,
}: {
  readonly id: string;
  readonly label: string;
  readonly required?: boolean;
  readonly placeholder?: string;
  readonly inputMode?: 'decimal' | 'numeric';
}) {
  return (
    <div>
      <label htmlFor={id} className={LABEL}>
        {label}
      </label>
      <input
        id={id}
        name={id}
        required={required}
        placeholder={placeholder}
        inputMode={inputMode}
        className={FIELD}
      />
    </div>
  );
}
