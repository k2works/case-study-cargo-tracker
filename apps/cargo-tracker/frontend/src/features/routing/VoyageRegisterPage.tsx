import { useState, type SubmitEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import {
  ALERT,
  BUTTON_PRIMARY,
  CARD,
  FIELD,
  LABEL,
  LINK,
  PAGE_TITLE,
  SECTION_TITLE,
} from '@/shared/ui/styles';
import {
  acceptedCargoTypeLabel,
  registerVoyage,
  type AcceptedCargoType,
  type MovementInput,
} from './api';

const CARGO_TYPES: readonly AcceptedCargoType[] = ['GENERAL', 'HAZARDOUS', 'REEFER'];

/**
 * 入力中の区間。**鍵は位置ではなく行そのものが持つ。** 位置を鍵にすると、
 * 途中に行を挿したときに入力中の値が別の行へ移る。
 */
interface MovementRow extends MovementInput {
  readonly rowId: string;
}

let nextRowId = 0;

function emptyMovement(): MovementRow {
  nextRowId += 1;
  return {
    rowId: `movement-${nextRowId}`,
    departureUnLocode: '',
    arrivalUnLocode: '',
    departureAt: '',
    arrivalAt: '',
  };
}

/** 入力欄の値（datetime-local）を絶対時刻へ。港の時間帯が入るまでは UTC で送る。 */
function toInstant(local: string): string {
  return local ? `${local}:00Z` : '';
}

/**
 * S33 航海スケジュール登録（UC19 / US24）。
 *
 * <p>寄港地は順序つきで 1 件以上。<b>港の連結と時刻の前後は集約だけが見る。</b>
 * 画面は必須項目（{@code required}）と入力の形しか見ておらず、繋がっていない
 * 寄港地はサーバの 422 で分かる。同じ判断を 2 か所に置くと片方が古くなるため、
 * 判断はドメインの 1 か所に置いている。送る前に気づける形にするかは US25 で
 * 画面を編集可能にするときに決める。</p>
 *
 * <p>更新（/voyages/:no/edit）は US25（IT4）。IT3 は登録だけを作る。</p>
 */
export function VoyageRegisterPage() {
  const navigate = useNavigate();
  const [voyageNumber, setVoyageNumber] = useState('');
  const [carrierCode, setCarrierCode] = useState('');
  const [carrierName, setCarrierName] = useState('');
  const [vesselName, setVesselName] = useState('');
  const [movements, setMovements] = useState<MovementRow[]>([emptyMovement()]);
  // 既定で一般貨物を選んでおく。選び忘れるとその航海が候補から消える。
  const [cargoTypes, setCargoTypes] = useState<AcceptedCargoType[]>(['GENERAL']);

  const mutation = useMutation({
    mutationFn: registerVoyage,
    onSuccess: () => navigate('/voyages', { state: { justRegistered: true } }),
  });

  function updateMovement(index: number, patch: Partial<MovementInput>) {
    setMovements((current) =>
      current.map((movement, at) => (at === index ? { ...movement, ...patch } : movement)),
    );
  }

  function toggleCargoType(cargoType: AcceptedCargoType) {
    setCargoTypes((current) =>
      current.includes(cargoType)
        ? current.filter((type) => type !== cargoType)
        : [...current, cargoType],
    );
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate({
      voyageNumber,
      carrierCode,
      carrierName,
      vesselName,
      movements: movements.map((movement) => ({
        ...movement,
        departureAt: toInstant(movement.departureAt),
        arrivalAt: toInstant(movement.arrivalAt),
      })),
      acceptedCargoTypes: cargoTypes,
    });
  }

  const error = mutation.error;

  return (
    <section>
      <h1 className={PAGE_TITLE}>航海スケジュールを登録する</h1>
      <p className="mt-2 text-sm">
        <Link to="/voyages" className={LINK}>
          航海スケジュール一覧に戻る
        </Link>
      </p>

      {error instanceof ApiError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          {error.status === 409
            ? `${error.body.message}（登録済みの航海を開くか、番号を直してください）`
            : error.body.message}
        </p>
      )}
      {error !== null && !(error instanceof ApiError) && (
        <p role="alert" className={`${ALERT} mt-4`}>
          登録できませんでした
        </p>
      )}

      <form onSubmit={submit} className={`${CARD} mt-4 space-y-6`}>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className={LABEL}>
            <span>航海番号</span>
            <input
              className={FIELD}
              required
              value={voyageNumber}
              onChange={(event) => setVoyageNumber(event.target.value)}
            />
          </label>
          <label className={LABEL}>
            <span>運送会社コード</span>
            <input
              className={FIELD}
              required
              value={carrierCode}
              onChange={(event) => setCarrierCode(event.target.value)}
            />
          </label>
          <label className={LABEL}>
            <span>運送会社</span>
            <input
              className={FIELD}
              required
              value={carrierName}
              onChange={(event) => setCarrierName(event.target.value)}
            />
          </label>
          <label className={LABEL}>
            <span>船名</span>
            <input
              className={FIELD}
              required
              value={vesselName}
              onChange={(event) => setVesselName(event.target.value)}
            />
          </label>
        </div>

        <div>
          <h2 className={SECTION_TITLE}>寄港地（上から順に回ります）</h2>
          <p className="mt-1 text-sm text-gray-600">
            2 行目以降の出発地は前の行の到着地と同じにしてください。到着日時は出発日時より後です。
            {/* 運送会社の公開スケジュールは港の現地時刻。そのまま入れると時差の分
                ずれた航海が登録され、経路候補の所要日数までずれる。エラーは出ない
                ので、荷主に誤った到着日を出すまで誰も気づかない。 */}
            <strong className="block mt-1">
              日時は協定世界時（UTC）で入力してください。運送会社の公開スケジュールは
              港の現地時刻なので、換算してから入れてください。
            </strong>
          </p>
          {movements.map((movement, index) => (
            <fieldset key={movement.rowId} className="mt-3 grid gap-3 sm:grid-cols-4">
              <legend className="text-sm font-medium text-gray-700">{index + 1} 区間目</legend>
              <label className={LABEL}>
                <span>出発地</span>
                <input
                  className={FIELD}
                  required
                  value={movement.departureUnLocode}
                  onChange={(event) =>
                    updateMovement(index, { departureUnLocode: event.target.value })
                  }
                />
              </label>
              <label className={LABEL}>
                <span>出発日時（UTC）</span>
                <input
                  className={FIELD}
                  type="datetime-local"
                  required
                  value={movement.departureAt}
                  onChange={(event) => updateMovement(index, { departureAt: event.target.value })}
                />
              </label>
              <label className={LABEL}>
                <span>到着地</span>
                <input
                  className={FIELD}
                  required
                  value={movement.arrivalUnLocode}
                  onChange={(event) =>
                    updateMovement(index, { arrivalUnLocode: event.target.value })
                  }
                />
              </label>
              <label className={LABEL}>
                <span>到着日時（UTC）</span>
                <input
                  className={FIELD}
                  type="datetime-local"
                  required
                  value={movement.arrivalAt}
                  onChange={(event) => updateMovement(index, { arrivalAt: event.target.value })}
                />
              </label>
            </fieldset>
          ))}
          <button
            type="button"
            className="mt-3 text-sm text-blue-700 underline"
            onClick={() => setMovements((current) => [...current, emptyMovement()])}
          >
            寄港地を追加する
          </button>
        </div>

        <fieldset>
          <legend className={SECTION_TITLE}>対応する貨物種別</legend>
          <p className="mt-1 text-sm text-gray-600">
            選ばないと一般貨物のみになります。危険物・冷凍の予約は、その種別を選んだ航海だけが候補になります。
          </p>
          {CARGO_TYPES.map((cargoType) => (
            <label key={cargoType} className="mt-2 flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={cargoTypes.includes(cargoType)}
                onChange={() => toggleCargoType(cargoType)}
              />
              {acceptedCargoTypeLabel(cargoType)}
            </label>
          ))}
        </fieldset>

        <button type="submit" className={BUTTON_PRIMARY} disabled={mutation.isPending}>
          {mutation.isPending ? '送信中…' : '登録する'}
        </button>
      </form>
    </section>
  );
}
