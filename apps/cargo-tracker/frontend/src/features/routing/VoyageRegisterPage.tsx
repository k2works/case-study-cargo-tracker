import { useEffect, useState, type SubmitEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
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
} from '@/shared/ui/styles';
import {
  acceptedCargoTypeLabel,
  formatVoyageTime,
  diffVoyage,
  fetchVoyage,
  registerVoyage,
  updateVoyage,
  type AcceptedCargoType,
  type FieldChange,
  type MovementInput,
  type UpdateVoyageInput,
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

/** 絶対時刻を入力欄の値へ。送るときと同じ UTC の見方で戻す。 */
function toLocalInput(instant: string): string {
  return instant ? instant.slice(0, 16) : '';
}

/**
 * S33 航海スケジュール登録（UC19 / US24）。
 *
 * <p>寄港地は順序つきで 1 件以上。<b>港の連結と時刻の前後は集約だけが見る。</b>
 * 画面は必須項目（{@code required}）と入力の形しか見ておらず、繋がっていない
 * 寄港地はサーバの 422 で分かる。同じ判断を 2 か所に置くと片方が古くなるため、
 * 判断はドメインの 1 か所に置いている。<b>送る前に気づける形にはしない</b>
 * （IT4 で決定）。区間番号を載せたエラー文で「どこを直すか」は分かる。</p>
 *
 * <p>更新（/voyages/:no/edit）もこの画面が兼ねる（ui_design.md の画面一覧どおり
 * S33 が登録・更新の両方）。更新では差分を確かめてから送る。</p>
 */
/**
 * 差分に出す値を、画面の他の場所と同じ言葉にする。
 *
 * <p>差分は送信直前のいちばん慎重に読む場面なので、ここだけ列挙名と ISO 時刻が
 * 出ると読み違える。一覧・詳細と同じ「一般貨物」「2026-09-10 09:00 UTC」に揃える。</p>
 */
function displayValue(value: string): string {
  return value
    .split(' / ')
    .map((part) =>
      part.replace(/(\d{4}-\d{2}-\d{2}T[\d:]+Z)/g, (iso) => formatVoyageTime(iso)),
    )
    .map((part) => (/^[A-Z_]+$/.test(part) ? acceptedCargoTypeLabel(part) : part))
    .join(' / ');
}

/** 送信ボタンの文言。更新は差分の確認が先で、登録は送信中を出す。 */
function submitLabel(isEdit: boolean, isSubmitting: boolean): string {
  if (isEdit) {
    return '差分を確認する';
  }
  return isSubmitting ? '送信中…' : '登録する';
}

export function VoyageRegisterPage() {
  const navigate = useNavigate();
  // 経路に航海番号があれば更新（/voyages/:no/edit）。画面 ID は S33 のまま
  // （ui_design.md の画面一覧で S33 が登録と更新を兼ねる）。
  const { voyageNumber: editing } = useParams();
  const isEdit = Boolean(editing);
  const [voyageNumber, setVoyageNumber] = useState('');
  const [carrierCode, setCarrierCode] = useState('');
  const [carrierName, setCarrierName] = useState('');
  const [vesselName, setVesselName] = useState('');
  const [movements, setMovements] = useState<MovementRow[]>([emptyMovement()]);
  // 既定で一般貨物を選んでおく。選び忘れるとその航海が候補から消える。
  const [cargoTypes, setCargoTypes] = useState<AcceptedCargoType[]>(['GENERAL']);

  // 差分（US25 §受入基準 2）。null は「まだ確かめていない」。
  const [changes, setChanges] = useState<FieldChange[] | null>(null);
  // R.5: 航海はまとめて何十本も入れる作業なので、続けて入力できるようにする。
  const [keepEntering, setKeepEntering] = useState(false);
  const [registered, setRegistered] = useState<string | null>(null);

  const existing = useQuery({
    queryKey: ['voyage', editing],
    queryFn: () => fetchVoyage(editing ?? ''),
    enabled: isEdit,
  });

  // 既登録の内容を入力欄に入れる（US25 §受入基準 1）。
  useEffect(() => {
    if (existing.data?.state !== 'ready') {
      return;
    }
    const voyage = existing.data.value;
    setVoyageNumber(voyage.voyageNumber);
    setCarrierCode(voyage.carrierCode);
    setCarrierName(voyage.carrierName);
    setVesselName(voyage.vesselName);
    setCargoTypes([...voyage.acceptedCargoTypes]);
    setMovements(
      voyage.movements.map((movement) => ({
        ...emptyMovement(),
        departureUnLocode: movement.departureUnLocode,
        arrivalUnLocode: movement.arrivalUnLocode,
        departureAt: toLocalInput(movement.departureAt),
        arrivalAt: toLocalInput(movement.arrivalAt),
      })),
    );
  }, [existing.data]);

  const mutation = useMutation({
    mutationFn: registerVoyage,
    onSuccess: (result) => {
      if (keepEntering) {
        // 続けて入力する。運送会社と船は同じことが多いので残し、
        // 航海番号と寄港地だけを空にする。全部消すと入れ直しになる。
        setRegistered(result.voyageNumber);
        setVoyageNumber('');
        setMovements([emptyMovement()]);
        return;
      }
      navigate('/voyages', { state: { justRegistered: true } });
    },
  });

  const [pendingMessage, setPendingMessage] = useState<string | null>(null);
  const [confirmedPayload, setConfirmedPayload] = useState<UpdateVoyageInput | null>(null);

  const diff = useMutation({
    mutationFn: () => {
      // 確認した内容をそのまま送るために、この時点の入力を控える。
      // 押した瞬間に組み立て直すと、確認のあとに直した値が黙って送られる。
      const confirmed = payload();
      setConfirmedPayload(confirmed);
      return diffVoyage(editing ?? '', confirmed);
    },
    onSuccess: (result) => {
      if ('changes' in result) {
        setPendingMessage(null);
        setChanges(result.changes);
        return;
      }
      // 投影がまだ。比べる相手が無いので「変更なし」と見せない。
      setChanges(null);
      setPendingMessage(result.message);
    },
  });

  const update = useMutation({
    // 確認した内容をそのまま送る（US25 §受入基準 3）。
    mutationFn: () => updateVoyage(editing ?? '', confirmedPayload ?? payload()),
    onSuccess: () => navigate(`/voyages/${encodeURIComponent(editing ?? '')}`),
  });

  /**
   * 入力が変わったら、確認した差分を捨てる。
   *
   * <p>残したままにすると「確認した内容」と「送る内容」が食い違い、差分の確認が
   * 形だけになる。運航変更の入力は、確認のあとにもう一桁直す操作が普通に起きる。</p>
   */
  function invalidateConfirmation() {
    setChanges(null);
    setConfirmedPayload(null);
  }

  function updateMovement(index: number, patch: Partial<MovementInput>) {
    invalidateConfirmation();
    setMovements((current) =>
      current.map((movement, at) => (at === index ? { ...movement, ...patch } : movement)),
    );
  }

  function toggleCargoType(cargoType: AcceptedCargoType) {
    invalidateConfirmation();
    setCargoTypes((current) =>
      current.includes(cargoType)
        ? current.filter((type) => type !== cargoType)
        : [...current, cargoType],
    );
  }

  function payload() {
    return {
      carrierCode,
      carrierName,
      vesselName,
      movements: movements.map((movement) => ({
        departureUnLocode: movement.departureUnLocode,
        arrivalUnLocode: movement.arrivalUnLocode,
        departureAt: toInstant(movement.departureAt),
        arrivalAt: toInstant(movement.arrivalAt),
      })),
      acceptedCargoTypes: cargoTypes,
    };
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isEdit) {
      // 更新は差分を確かめてから送る（US25 §受入基準 2・3）。
      diff.mutate();
      return;
    }
    mutation.mutate({ voyageNumber, ...payload() });
  }

  const error = mutation.error ?? diff.error ?? update.error;

  return (
    <section>
      <h1 className={PAGE_TITLE}>
        {isEdit ? '航海スケジュールを更新する' : '航海スケジュールを登録する'}
      </h1>
      <p className="mt-2 text-sm">
        <Link to="/voyages" className={LINK}>
          航海スケジュール一覧に戻る
        </Link>
      </p>

      {pendingMessage !== null && (
        <output className={`${NOTICE} mt-4 block`}>{pendingMessage}</output>
      )}

      {registered !== null && (
        <output className={`${NOTICE} mt-4 block`}>
          {registered} を登録しました。続けて入力できます
        </output>
      )}

      {error instanceof ApiError && (
        <p role="alert" className={`${ALERT} mt-4`}>
          {error.status === 409 && !isEdit ? (
            <>
              {`${error.body.message}（`}
              {/* 案内の指す先を実際に開けるようにする（IT3 レビュー）。 */}
              <Link to={`/voyages/${encodeURIComponent(voyageNumber)}`} className={LINK}>
                登録済みの航海を開く
              </Link>
              {'か、番号を直してください）'}
            </>
          ) : (
            error.body.message
          )}
        </p>
      )}
      {error !== null && !(error instanceof ApiError) && (
        <p role="alert" className={`${ALERT} mt-4`}>
          登録できませんでした
        </p>
      )}

      <form onSubmit={submit} className={`${CARD} mt-4 space-y-6`}>
        <div className="grid gap-4 sm:grid-cols-2">
          {/* 航海番号は不変（不変条件 1）。更新では直せる欄として出さない。
              出すと、別の航海を作ったつもりで既存を壊す操作に見える。 */}
          {isEdit ? (
            <p className="text-sm text-gray-700">
              航海番号: <span className="font-medium">{voyageNumber}</span>
            </p>
          ) : (
            <label className={LABEL}>
              <span>航海番号</span>
              <input
                className={FIELD}
                required
                value={voyageNumber}
                onChange={(event) => setVoyageNumber(event.target.value)}
              />
            </label>
          )}
          <label className={LABEL}>
            <span>運送会社コード</span>
            <input
              className={FIELD}
              required
              value={carrierCode}
              onChange={(event) => {
                invalidateConfirmation();
                setCarrierCode(event.target.value);
              }}
            />
          </label>
          <label className={LABEL}>
            <span>運送会社名</span>
            <input
              className={FIELD}
              required
              value={carrierName}
              onChange={(event) => {
                invalidateConfirmation();
                setCarrierName(event.target.value);
              }}
            />
          </label>
          <label className={LABEL}>
            <span>船名</span>
            <input
              className={FIELD}
              required
              value={vesselName}
              onChange={(event) => {
                invalidateConfirmation();
                setVesselName(event.target.value);
              }}
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

        {!isEdit && (
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={keepEntering}
              onChange={(event) => setKeepEntering(event.target.checked)}
            />
            {'登録して続けて入力する'}
          </label>
        )}

        <button
          type="submit"
          className={BUTTON_PRIMARY}
          disabled={mutation.isPending || diff.isPending}
        >
          {submitLabel(isEdit, mutation.isPending)}
        </button>

        {/* 差分を確かめてから更新する。ここで初めて PUT を送る。
            「キャンセル」は何も送らない（US25 §受入基準 5）。 */}
        {changes !== null && (
          <div className={`${CARD} mt-2 space-y-2 text-sm`}>
            <h2 className={SECTION_TITLE}>更新の内容</h2>
            {changes.length === 0 ? (
              <>
                <p>変更はありません</p>
                <button
                  type="button"
                  className="text-sm text-blue-700 underline"
                  onClick={() => setChanges(null)}
                >
                  閉じる
                </button>
              </>
            ) : (
              <>
                <ul className="list-disc pl-5">
                  {changes.map((change) => (
                    <li key={change.label}>
                      <span className="font-medium">{change.label}</span>{' '}
                      <span>{`${displayValue(change.before)} → ${displayValue(change.after)}`}</span>
                    </li>
                  ))}
                </ul>
                <div className="flex gap-3">
                  <button
                    type="button"
                    className={BUTTON_PRIMARY}
                    disabled={update.isPending}
                    onClick={() => update.mutate()}
                  >
                    {update.isPending ? '送信中…' : '更新する'}
                  </button>
                  <button
                    type="button"
                    className="text-sm text-blue-700 underline"
                    onClick={() => setChanges(null)}
                  >
                    キャンセル
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </form>
    </section>
  );
}
