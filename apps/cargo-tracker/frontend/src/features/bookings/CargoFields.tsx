import { businessDate } from '@/shared/api/businessDate';
import { FIELD, LABEL } from '@/shared/ui/styles';
import type { CargoType } from './api';

/**
 * 貨物仕様と輸送条件の入力欄。登録（US04）と修正（US32）で同じものを使う。
 *
 * <p><b>2 つの画面に写さない。</b> 写すと、片方にだけ項目を足したときに
 * 「登録では入れられるのに修正では消える」が生まれる。サーバ側で受付と修正の入力を
 * 同じ形（{@code CargoFields}）にしているのと同じ理由。</p>
 *
 * <p>種別ごとの入力欄は<b>その種別を選んだときだけ</b>出す。常に出すと
 * 「一般貨物なのに危険物申告を求められる」ことになる。</p>
 */
export interface CargoFieldDefaults {
  readonly originUnLocode?: string;
  readonly destinationUnLocode?: string;
  readonly arrivalDeadline?: string;
  readonly weightKg?: string;
  readonly quantity?: string;
  readonly lengthCm?: string;
  readonly widthCm?: string;
  readonly heightCm?: string;
  readonly productName?: string;
  readonly hazardImoClass?: string;
  readonly hazardUnNumber?: string;
  readonly temperatureMinC?: string;
  readonly temperatureMaxC?: string;
}

export function CargoFields({
  cargoType,
  onCargoTypeChange,
  defaults = {},
  minArrivalDeadline = businessDate(),
}: {
  readonly cargoType: CargoType;
  readonly onCargoTypeChange: (cargoType: CargoType) => void;
  readonly defaults?: CargoFieldDefaults;
  readonly minArrivalDeadline?: string;
}) {
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="originUnLocode"
          label="出発地"
          required
          placeholder="JPTYO"
          defaultValue={defaults.originUnLocode}
        />
        <Field
          id="destinationUnLocode"
          label="目的地"
          required
          placeholder="USNYC"
          defaultValue={defaults.destinationUnLocode}
        />
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
          min={minArrivalDeadline}
          defaultValue={defaults.arrivalDeadline}
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
                onChange={() => onCargoTypeChange(value)}
                aria-label={label}
              />
              {label}
            </label>
          ))}
        </div>
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="weightKg"
          label="重量 (kg)"
          required
          inputMode="decimal"
          defaultValue={defaults.weightKg}
        />
        <Field
          id="quantity"
          label="数量"
          required
          inputMode="numeric"
          defaultValue={defaults.quantity}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field
          id="lengthCm"
          label="長さ (cm)"
          required
          inputMode="decimal"
          defaultValue={defaults.lengthCm}
        />
        <Field
          id="widthCm"
          label="幅 (cm)"
          required
          inputMode="decimal"
          defaultValue={defaults.widthCm}
        />
        <Field
          id="heightCm"
          label="高さ (cm)"
          required
          inputMode="decimal"
          defaultValue={defaults.heightCm}
        />
      </div>

      <Field id="productName" label="品名" required defaultValue={defaults.productName} />

      {/* 危険物を選んだときだけ現れる。 */}
      {cargoType === 'HAZARDOUS' && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="hazardImoClass"
            label="IMO クラス"
            required
            defaultValue={defaults.hazardImoClass}
          />
          <Field
            id="hazardUnNumber"
            label="UN 番号"
            required
            defaultValue={defaults.hazardUnNumber}
          />
        </div>
      )}

      {/* 冷凍・冷蔵を選んだときだけ現れる。 */}
      {cargoType === 'REFRIGERATED' && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="temperatureMinC"
            label="温度条件（下限 ℃）"
            required
            inputMode="decimal"
            defaultValue={defaults.temperatureMinC}
          />
          <Field
            id="temperatureMaxC"
            label="温度条件（上限 ℃）"
            required
            inputMode="decimal"
            defaultValue={defaults.temperatureMaxC}
          />
        </div>
      )}
    </>
  );
}

function Field({
  id,
  label,
  required,
  placeholder,
  inputMode,
  defaultValue,
}: {
  readonly id: string;
  readonly label: string;
  readonly required?: boolean;
  readonly placeholder?: string;
  readonly inputMode?: 'decimal' | 'numeric';
  readonly defaultValue?: string;
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
        defaultValue={defaultValue}
        className={FIELD}
      />
    </div>
  );
}
