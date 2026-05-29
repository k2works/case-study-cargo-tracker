import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  registerException,
  type ExceptionType,
  type RegisterExceptionRequestBody,
} from '../api/trackingApi';

/**
 * S18 例外登録（US19 / US20 / IT6 タスク 2.4）。
 *
 * <p>追跡詳細から「例外を記録」で遷移し、種別（遅延/破損/紛失）と発生状況を登録する。
 * 紛失（LOSS）選択時は管理職 escalation の確認チェックボックスを必須にし、誤操作を防ぐ。</p>
 *
 * <p>登録成功で /tracking/:trackingNumber/manage に PRG 遷移する（貨物状態が EXCEPTION に変更されたことを確認する流れ）。</p>
 */
export default function ExceptionRegisterPage() {
  const { trackingNumber = '' } = useParams<{ trackingNumber: string }>();
  const navigate = useNavigate();
  const [type, setType] = useState<ExceptionType>('DELAY');
  const [occurredAt, setOccurredAt] = useState(() => defaultOccurredAt());
  const [occurredUnlocode, setOccurredUnlocode] = useState('');
  const [description, setDescription] = useState('');
  const [lossAcknowledged, setLossAcknowledged] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(ev: { preventDefault: () => void }) {
    ev.preventDefault();
    if (!description.trim()) {
      setError('発生状況・理由を入力してください');
      return;
    }
    if (type === 'LOSS' && !lossAcknowledged) {
      setError('紛失を登録するには escalation 通知の確認が必要です');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const body: RegisterExceptionRequestBody = {
        type,
        occurredAt,
        occurredUnlocode: occurredUnlocode || null,
        description,
      };
      await registerException(trackingNumber, body);
      navigate(`/tracking/${trackingNumber}/manage`, {
        state: { notice: '例外を記録しました' },
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : '例外登録に失敗しました');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">
          例外登録 - {trackingNumber}
        </h1>
        <button
          type="button"
          onClick={() => navigate(`/tracking/${trackingNumber}/manage`)}
          className="text-sm text-blue-600 hover:underline"
        >
          追跡詳細に戻る
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border bg-white p-4 shadow-sm">
        <fieldset>
          <legend className="block text-sm font-medium text-gray-700">例外種別</legend>
          <div className="mt-2 flex gap-4">
            {(['DELAY', 'DAMAGE', 'LOSS'] as ExceptionType[]).map((t) => (
              <label key={t} className="inline-flex items-center gap-1 text-sm">
                <input
                  type="radio"
                  name="exceptionType"
                  value={t}
                  checked={type === t}
                  onChange={() => {
                    setType(t);
                    if (t !== 'LOSS') setLossAcknowledged(false);
                  }}
                />
                {t === 'DELAY' ? '遅延' : t === 'DAMAGE' ? '破損' : '紛失'}
              </label>
            ))}
          </div>
        </fieldset>

        <label className="block text-sm font-medium text-gray-700">
          <span>発生日時</span>
          <input
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
            required
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          <span>発生場所 (UN/LOCODE)</span>
          <input
            type="text"
            value={occurredUnlocode}
            onChange={(e) => setOccurredUnlocode(e.target.value.toUpperCase())}
            maxLength={5}
            placeholder="SGSIN"
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          <span>発生状況・理由</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            required
            placeholder="悪天候のため寄港不可、新到着予定 ..."
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        {type === 'LOSS' && (
          <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            <p className="font-semibold">⚠ 紛失を選択しました</p>
            <p className="mt-1">
              この操作により管理職（追跡管理マネージャー他）に自動で escalation 通知が送信されます。
              本当に紛失として登録する場合は下のチェックを入れてください。
            </p>
            <label className="mt-2 inline-flex items-center gap-2">
              <input
                type="checkbox"
                checked={lossAcknowledged}
                onChange={(e) => setLossAcknowledged(e.target.checked)}
              />
              <span>紛失であることを確認しました（escalation を承知のうえ登録する）</span>
            </label>
          </div>
        )}

        <div className="flex gap-2">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {submitting ? '送信中…' : '例外を記録'}
          </button>
          <button
            type="button"
            onClick={() => navigate(`/tracking/${trackingNumber}/manage`)}
            className="rounded-md bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
          >
            キャンセル
          </button>
        </div>
      </form>
    </div>
  );
}

function defaultOccurredAt(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}
