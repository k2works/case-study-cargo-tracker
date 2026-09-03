import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { ALERT, BUTTON_PRIMARY, CARD, FIELD, LABEL, PAGE_TITLE } from '@/shared/ui/styles';
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
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? '');
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
        <Field id="shipperId" label="荷主 ID" required />

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="originUnLocode" label="出発地" required placeholder="JPTYO" />
          <Field id="destinationUnLocode" label="目的地" required placeholder="USNYC" />
        </div>

        <div>
          <label htmlFor="arrivalDeadline" className={LABEL}>
            到着期限
          </label>
          <input id="arrivalDeadline" name="arrivalDeadline" type="date" required className={FIELD} />
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
