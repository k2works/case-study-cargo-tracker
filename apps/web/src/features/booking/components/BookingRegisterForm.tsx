import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Alert } from '@/components/ui/Alert';
import { Field } from '@/components/ui/Field';
import { ApiError, messageFor } from '@/lib/errors';
import { useIdempotencyKey } from '@/lib/idempotency';
import { generateUlid } from '@/lib/ulid';
import { useAwaitReflection, useRegisterBooking, useShippers, type RegisteredBooking } from '../api/hooks';

const EMPTY = {
  shipperId: '',
  itemName: '',
  weightKg: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
  quantity: '',
  origin: '',
  destination: '',
  preferredHandover: '',
  deadline: '',
};

/** SC-26 予約登録（一般貨物。US04）。結果整合：受け付けた結果を先に示し、一覧への反映は応答の版に追いつくまで待つ。 */
export function BookingRegisterForm({ initialShipperId = '' }: { initialShipperId?: string }) {
  const [values, setValues] = useState({ ...EMPTY, shipperId: initialShipperId });
  const [error, setError] = useState<ApiError | null>(null);
  const [accepted, setAccepted] = useState<RegisteredBooking | null>(null);
  // 画面を開いた時点で予約 ID（ULID）と冪等性キーを 1 つずつ作る。再押下・再送では同じものを使う（ADR-003）
  const [bookingId, setBookingId] = useState(() => generateUlid());
  const idempotency = useIdempotencyKey();
  const shippers = useShippers('');
  const register = useRegisterBooking();
  const reflection = useAwaitReflection(accepted ? { bookingId: accepted.bookingId, version: accepted.version } : null);

  const set = (name: keyof typeof EMPTY) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [name]: e.target.value }));
  const fieldError = (...names: string[]) => error?.fields.find((f) => names.includes(f.field))?.reason;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    // 個数は整数だけ。黙って丸めず（2.5 → 2）、送る前に示す
    if (!/^[0-9]+$/.test(values.quantity.trim())) {
      setError(new ApiError('business', 'VALIDATION_FAILED', [{ field: 'quantity', reason: '1 以上の整数で入力してください' }]));
      return;
    }
    register.mutate(
      {
        idempotencyKey: idempotency.key,
        bookingId,
        shipperId: values.shipperId,
        itemName: values.itemName,
        weightKg: values.weightKg,
        lengthCm: values.lengthCm,
        widthCm: values.widthCm,
        heightCm: values.heightCm,
        quantity: Number.parseInt(values.quantity.trim(), 10),
        origin: values.origin,
        destination: values.destination,
        preferredHandover: values.preferredHandover,
        deadline: values.deadline,
      },
      { onSuccess: (result) => setAccepted(result), onError: setError },
    );
  };

  const again = () => {
    setAccepted(null);
    setValues({ ...EMPTY, shipperId: values.shipperId });
    setBookingId(generateUlid());
    idempotency.reset();
  };

  if (accepted) {
    return (
      <section aria-label="予約の登録結果">
        <Alert kind="info">受け付けました（予約番号 {accepted.number}）</Alert>
        {reflection.reflected ? (
          <p>
            一覧に反映されました。 <Link to={`/bookings/${accepted.bookingId}`}>予約の詳細を開く</Link>
          </p>
        ) : reflection.timedOut ? (
          <p>
            反映待ちです。しばらくしてから「更新」を押してください。 <button type="button" onClick={reflection.retry}>更新</button>
          </p>
        ) : (
          <p>一覧への反映を待っています</p>
        )}
        <button type="button" onClick={again}>
          続けて登録する
        </button>
      </section>
    );
  }

  if (shippers.data && shippers.data.length === 0) {
    return (
      <p>
        荷主がまだ登録されていません。先に <Link to="/shippers/new">荷主を登録する</Link> 必要があります。
      </p>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate aria-label="予約登録">
      <div className="field">
        <label htmlFor="shipper">荷主</label>
        <select
          id="shipper"
          value={values.shipperId}
          onChange={set('shipperId')}
          aria-label="荷主"
          aria-invalid={fieldError('shipperId') ? true : undefined}
          aria-describedby={fieldError('shipperId') ? 'shipper-error' : undefined}
        >
          <option value="">選択してください</option>
          {shippers.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        {fieldError('shipperId') && <span id="shipper-error">{fieldError('shipperId')}</span>}
      </div>
      <Field label="品名" value={values.itemName} onChange={set('itemName')} error={fieldError('itemName')} required />
      <Field label="重量（kg）" inputMode="decimal" value={values.weightKg} onChange={set('weightKg')} error={fieldError('weightKg')} required />
      <Field label="長さ（cm）" inputMode="decimal" value={values.lengthCm} onChange={set('lengthCm')} error={fieldError('dimensions', 'lengthCm')} required />
      <Field label="幅（cm）" inputMode="decimal" value={values.widthCm} onChange={set('widthCm')} error={fieldError('dimensions', 'widthCm')} required />
      <Field label="高さ（cm）" inputMode="decimal" value={values.heightCm} onChange={set('heightCm')} error={fieldError('dimensions', 'heightCm')} required />
      <Field label="個数" inputMode="numeric" value={values.quantity} onChange={set('quantity')} error={fieldError('quantity')} required />
      <Field label="出発地（UN/LOCODE）" value={values.origin} onChange={set('origin')} error={fieldError('origin')} required />
      <Field label="目的地（UN/LOCODE）" value={values.destination} onChange={set('destination')} error={fieldError('destination')} required />
      <Field label="希望引渡日" type="date" value={values.preferredHandover} onChange={set('preferredHandover')} error={fieldError('preferredHandover')} required />
      <Field label="希望期限" type="date" value={values.deadline} onChange={set('deadline')} error={fieldError('deadline')} required />

      {error && <Alert kind="error">{messageFor(error)}</Alert>}
      <button type="submit" disabled={register.isPending}>
        予約を登録
      </button>
    </form>
  );
}
