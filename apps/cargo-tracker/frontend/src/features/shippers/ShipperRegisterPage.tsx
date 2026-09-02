import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { registerShipper } from './api';

type ShipperType = 'INDIVIDUAL' | 'CORPORATE';

/** S11 荷主登録（UC02 / US02）。 */
export function ShipperRegisterPage() {
  const [shipperType, setShipperType] = useState<ShipperType>('INDIVIDUAL');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    try {
      await registerShipper({
        name: String(form.get('name') ?? ''),
        shipperType,
        email: String(form.get('email') ?? ''),
        phone: String(form.get('phone') ?? ''),
        address: String(form.get('address') ?? ''),
        contractNumber:
          shipperType === 'CORPORATE' ? String(form.get('contractNumber') ?? '') : undefined,
        discountRate:
          shipperType === 'CORPORATE' ? String(form.get('discountRate') ?? '') : undefined,
      });
      // 受け付けただけで一覧にはまだ出ない。一覧側が取り直せるようにしてから移る。
      await queryClient.invalidateQueries({ queryKey: ['shippers'] });
      navigate('/shippers', { state: { justRegistered: true } });
    } catch (e) {
      setError(
        e instanceof ApiError ? e.body.message : '登録できませんでした。もう一度お試しください',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h1>荷主登録</h1>
      <form onSubmit={onSubmit}>
        <label htmlFor="name">名称</label>
        <input id="name" name="name" required />

        <fieldset>
          <legend>種別</legend>
          <label htmlFor="type-individual">個人</label>
          <input
            id="type-individual"
            type="radio"
            name="shipperType"
            value="INDIVIDUAL"
            checked={shipperType === 'INDIVIDUAL'}
            onChange={() => setShipperType('INDIVIDUAL')}
          />
          <label htmlFor="type-corporate">法人</label>
          <input
            id="type-corporate"
            type="radio"
            name="shipperType"
            value="CORPORATE"
            checked={shipperType === 'CORPORATE'}
            onChange={() => setShipperType('CORPORATE')}
          />
        </fieldset>

        <label htmlFor="email">メールアドレス</label>
        <input id="email" name="email" type="email" required />

        <label htmlFor="phone">電話番号</label>
        <input id="phone" name="phone" />

        <label htmlFor="address">住所</label>
        <input id="address" name="address" />

        {/* 法人のときだけ出す。常に出すと「個人なのに契約番号を求められる」ことになる。 */}
        {shipperType === 'CORPORATE' && (
          <>
            <label htmlFor="contractNumber">契約番号</label>
            <input id="contractNumber" name="contractNumber" required />

            <label htmlFor="discountRate">割引率（0.0000〜0.3000）</label>
            <input id="discountRate" name="discountRate" defaultValue="0.0000" required />
          </>
        )}

        <button type="submit" aria-disabled={submitting}>
          {submitting ? '登録中…' : '登録する'}
        </button>
      </form>

      {error !== null && <p role="alert">{error}</p>}
    </section>
  );
}
