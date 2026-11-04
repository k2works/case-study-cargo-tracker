import { useState, type FormEvent } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Field } from '@/components/ui/Field';
import { ApiError, messageFor } from '@/lib/errors';
import { useIdempotencyKey } from '@/lib/idempotency';
import { useRegisterShipper, useShipper } from '../api/hooks';

type Props = {
  onRegistered: (shipperId: string) => void;
  onUseExisting: (shipperId: string) => void;
};

/** SC-21 荷主登録（個人。US02）。 */
export function ShipperRegisterForm({ onRegistered, onUseExisting }: Props) {
  const [values, setValues] = useState({ name: '', address: '', contact: '', email: '' });
  const [error, setError] = useState<ApiError | null>(null);
  const idempotency = useIdempotencyKey();
  // 重複を承知して続けるときは内容が変わる（同じキーで内容が違うとサーバーが拒む）ので、別のキーを使う。
  // 通信エラーの後の再送では、このキーを使い回す（二重に登録しない。ADR-003）
  const acknowledged = useIdempotencyKey();
  const register = useRegisterShipper();

  const set = (name: keyof typeof values) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [name]: e.target.value }));
  const fieldError = (field: string) => error?.fields.find((f) => f.field === field)?.reason;

  const submit = (acknowledgedDuplicate: boolean) => {
    setError(null);
    register.mutate(
      { idempotencyKey: acknowledgedDuplicate ? acknowledged.key : idempotency.key, ...values, acknowledgedDuplicate },
      {
        onSuccess: (shipperId) => {
          idempotency.reset();
          acknowledged.reset();
          onRegistered(shipperId);
        },
        onError: setError,
      },
    );
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit(false);
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="荷主登録">
      <Field label="氏名または社名" value={values.name} onChange={set('name')} error={fieldError('name')} required />
      <Field label="住所" value={values.address} onChange={set('address')} error={fieldError('address')} required />
      <Field label="連絡先" value={values.contact} onChange={set('contact')} error={fieldError('contact')} required />
      <Field label="メールアドレス" type="email" value={values.email} onChange={set('email')} error={fieldError('email')} required />

      {error?.code === 'DUPLICATE_EMAIL' ? (
        <Alert kind="warning">
          {messageFor(error)}
          {error.existingShipperId && <ExistingShipper shipperId={error.existingShipperId} />}
          <div>
            {error.existingShipperId && (
              <button type="button" onClick={() => onUseExisting(error.existingShipperId!)}>
                既存の荷主を使う
              </button>
            )}
            <button type="button" onClick={() => submit(true)} disabled={register.isPending}>
              登録を続ける
            </button>
          </div>
        </Alert>
      ) : (
        error && <Alert kind="error">{messageFor(error)}</Alert>
      )}

      <button type="submit" disabled={register.isPending}>
        荷主を登録
      </button>
    </form>
  );
}

/** 重複した既存の荷主。同じ人か、同じ会社の別担当者かを見分けられるように示す（US02 AC-02）。 */
function ExistingShipper({ shipperId }: { shipperId: string }) {
  const q = useShipper(shipperId);
  const s = q.data;
  if (!s) return null;
  return (
    <dl aria-label="既存の荷主">
      <dt>氏名または社名</dt>
      <dd>{s.name}</dd>
      <dt>住所</dt>
      <dd>{s.address}</dd>
      <dt>連絡先</dt>
      <dd>{s.contact}</dd>
    </dl>
  );
}
