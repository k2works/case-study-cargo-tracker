import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  NOTICE,
  PAGE_TITLE,
} from '@/shared/ui/styles';
import type { CargoType } from '@/shared/domain/cargoType';
import { createQuotation } from './api';

/**
 * S12 見積作成（US01 §受入基準 1・6）。
 *
 * <p><b>5 項目を聞く</b>——出発地・目的地・希望期限・貨物種別・重量（正典の
 * 不変条件 1）。候補と概算はサーバが数える。</p>
 *
 * <p><b>危険物のときだけ危険物申告を出す。</b> 使わない欄を常に並べると、
 * 入力の手数が増えるだけでなく「書かなくてよい欄」に見える。</p>
 *
 * <p><b>作ったら詳細へ移る。</b> 見積番号は控えるものなので、作りっぱなしに
 * すると営業担当者は番号を探しに戻ることになる。</p>
 */
export function QuotationCreatePage() {
  const navigate = useNavigate();

  const [originUnLocode, setOriginUnLocode] = useState('');
  const [destinationUnLocode, setDestinationUnLocode] = useState('');
  const [arrivalDeadline, setArrivalDeadline] = useState('');
  const [cargoType, setCargoType] = useState<CargoType>('GENERAL');
  const [weightKg, setWeightKg] = useState('');
  const [hazardousImoClass, setHazardousImoClass] = useState('');
  const [hazardousUnNumber, setHazardousUnNumber] = useState('');

  const create = useMutation({
    mutationFn: () => createQuotation({
      originUnLocode: originUnLocode.trim().toUpperCase(),
      destinationUnLocode: destinationUnLocode.trim().toUpperCase(),
      arrivalDeadline,
      cargoType,
      // **数値で送る。** 文字列でも Jackson が救うが、予約（S21）は数値で
      // 送っており、形が揃っていないと片方の変更が他方に効かない。
      weightKg: Number(weightKg),
      hazardousImoClass: cargoType === 'HAZARDOUS' ? hazardousImoClass.trim() : null,
      hazardousUnNumber: cargoType === 'HAZARDOUS' ? hazardousUnNumber.trim() : null,
    }),
    onSuccess: (created) => navigate(`/quotations/${created.quotationId}`),
  });

  return (
    <section>
      <h1 className={PAGE_TITLE}>見積作成</h1>
      <p className="mt-1 text-sm text-gray-600">
        輸送要件を入れると、ルート候補と概算料金が出ます。概算は請求と同じ料率で数えますが、
        実際に通る区間は輸送のときに決まるため、請求額とは一致しないことがあります。
      </p>

      <form
        className={`${CARD} mt-4 grid gap-4 sm:grid-cols-2`}
        onSubmit={(event) => {
          event.preventDefault();
          create.mutate();
        }}
      >
        <div>
          <label className={LABEL} htmlFor="quotation-origin">出発地</label>
          <input
            id="quotation-origin"
            className={FIELD}
            value={originUnLocode}
            onChange={(event) => setOriginUnLocode(event.target.value)}
          />
        </div>
        <div>
          <label className={LABEL} htmlFor="quotation-destination">目的地</label>
          <input
            id="quotation-destination"
            className={FIELD}
            value={destinationUnLocode}
            onChange={(event) => setDestinationUnLocode(event.target.value)}
          />
        </div>
        <div>
          <label className={LABEL} htmlFor="quotation-deadline">希望到着期限</label>
          <input
            id="quotation-deadline"
            className={FIELD}
            type="date"
            value={arrivalDeadline}
            onChange={(event) => setArrivalDeadline(event.target.value)}
          />
        </div>
        <div>
          <label className={LABEL} htmlFor="quotation-cargo-type">貨物種別</label>
          <select
            id="quotation-cargo-type"
            className={FIELD}
            value={cargoType}
            onChange={(event) => setCargoType(event.target.value as CargoType)}
          >
            <option value="GENERAL">一般</option>
            <option value="HAZARDOUS">危険物</option>
            <option value="REFRIGERATED">冷凍・冷蔵</option>
          </select>
        </div>
        <div>
          <label className={LABEL} htmlFor="quotation-weight">重量（kg）</label>
          <input
            id="quotation-weight"
            className={FIELD}
            type="number"
            value={weightKg}
            onChange={(event) => setWeightKg(event.target.value)}
          />
        </div>

        {/* **危険物のときだけ出す**（US01 §受入基準 6）。見積の時点で聞いて
            おかないと、予約で初めて断られて出し直しになる。 */}
        {cargoType === 'HAZARDOUS' && (
          <>
            <div>
              <label className={LABEL} htmlFor="quotation-imo">IMO クラス</label>
              <input
                id="quotation-imo"
                className={FIELD}
                value={hazardousImoClass}
                onChange={(event) => setHazardousImoClass(event.target.value)}
              />
            </div>
            <div>
              <label className={LABEL} htmlFor="quotation-un">UN 番号</label>
              <input
                id="quotation-un"
                className={FIELD}
                value={hazardousUnNumber}
                onChange={(event) => setHazardousUnNumber(event.target.value)}
              />
            </div>
          </>
        )}

        <div className="sm:col-span-2">
          <button className={BUTTON_PRIMARY} type="submit" disabled={create.isPending}>
            見積を作る
          </button>
          {create.isPending && <output className={`${NOTICE} ml-3`}>送信中…</output>}
        </div>
      </form>

      {create.isError && (
        <p role="alert" className={`${ALERT} mt-3`}>
          {create.error instanceof ApiError
            ? create.error.message : '見積を作れませんでした'}
        </p>
      )}
    </section>
  );
}
