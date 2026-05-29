import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  handlingTypeLabel,
  registerHandling,
  requiresClaimVerification,
  requiresVoyageNumber,
  type HandlingType,
} from '../api/handlingApi';

const HANDLING_TYPES: HandlingType[] = ['RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS'];

export default function HandlingFormPage() {
  const navigate = useNavigate();
  const [trackingNumber, setTrackingNumber] = useState('');
  const [handlingType, setHandlingType] = useState<HandlingType>('RECEIVE');
  const [unlocode, setUnlocode] = useState('');
  const [voyageNumber, setVoyageNumber] = useState('');
  const [occurredAt, setOccurredAt] = useState(() => defaultOccurredAt());
  const [handlerId, setHandlerId] = useState('');
  const [consigneeName, setConsigneeName] = useState('');
  const [signatureRef, setSignatureRef] = useState('');
  const [confirmationCode, setConfirmationCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const claimRequired = requiresClaimVerification(handlingType);
  const voyageRequired = requiresVoyageNumber(handlingType);

  async function handleSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    setError(null);
    if (voyageRequired && !voyageNumber) {
      setError('LOAD / UNLOAD では航海番号が必須です');
      return;
    }
    if (claimRequired) {
      if (!consigneeName) {
        setError('引取作業では荷受人氏名が必須です');
        return;
      }
      if (!signatureRef && !confirmationCode) {
        setError('引取作業では署名参照または確認コードのいずれかが必須です');
        return;
      }
    }
    setSubmitting(true);
    try {
      const res = await registerHandling({
        trackingNumber,
        handlingType,
        unlocode,
        voyageNumber: voyageNumber || null,
        occurredAt,
        handlerId,
        claimVerification: claimRequired
          ? {
              consigneeName,
              signatureRef: signatureRef || null,
              confirmationCode: confirmationCode || null,
              verifiedAt: occurredAt,
            }
          : null,
      });
      navigate(`/handling?activityId=${res.activityId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '登録に失敗しました');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">荷役作業記録</h1>
        <button
          type="button"
          onClick={() => navigate('/handling')}
          className="text-sm text-blue-600 hover:underline"
        >
          履歴に戻る
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-md bg-red-50 p-3">
          <p role="alert" className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-4 rounded-lg border bg-white p-4 shadow-sm">
        <label className="block text-sm font-medium text-gray-700">
          追跡番号
          <input
            type="text"
            value={trackingNumber}
            onChange={(e) => setTrackingNumber(e.target.value)}
            required
            placeholder="TRK-AB12CD3456"
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          作業種別
          <select
            value={handlingType}
            onChange={(e) => setHandlingType(e.target.value as HandlingType)}
            required
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          >
            {HANDLING_TYPES.map((t) => (
              <option key={t} value={t}>
                {handlingTypeLabel(t)}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-gray-700">
          作業場所（UN/LOCODE）
          <input
            type="text"
            value={unlocode}
            onChange={(e) => setUnlocode(e.target.value.toUpperCase())}
            maxLength={5}
            required
            placeholder="JPTYO"
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          作業日時
          <input
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
            required
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          航海番号 {voyageRequired && <span className="text-red-600">*</span>}
          <input
            type="text"
            value={voyageNumber}
            onChange={(e) => setVoyageNumber(e.target.value)}
            required={voyageRequired}
            placeholder="V-MAERSK-220"
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
          {voyageRequired && (
            <span className="text-xs text-gray-500">LOAD / UNLOAD では必須</span>
          )}
        </label>

        <label className="block text-sm font-medium text-gray-700">
          作業員 ID
          <input
            type="text"
            value={handlerId}
            onChange={(e) => setHandlerId(e.target.value)}
            required
            placeholder="H-001"
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        {claimRequired && (
          <fieldset className="rounded-md border border-blue-200 bg-blue-50 p-3">
            <legend className="text-sm font-semibold text-blue-700">荷受人確認（引取必須）</legend>
            <div className="space-y-3">
              <label className="block text-sm font-medium text-gray-700">
                荷受人氏名 <span className="text-red-600">*</span>
                <input
                  type="text"
                  value={consigneeName}
                  onChange={(e) => setConsigneeName(e.target.value)}
                  required
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <label className="block text-sm font-medium text-gray-700">
                署名参照（URL / ID）
                <input
                  type="text"
                  value={signatureRef}
                  onChange={(e) => setSignatureRef(e.target.value)}
                  placeholder="https://... or sign-12345"
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <label className="block text-sm font-medium text-gray-700">
                確認コード
                <input
                  type="text"
                  value={confirmationCode}
                  onChange={(e) => setConfirmationCode(e.target.value)}
                  placeholder="例: A1B2C3"
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <p className="text-xs text-gray-600">
                署名参照または確認コードのいずれかが必須
              </p>
            </div>
          </fieldset>
        )}

        <div className="flex gap-3">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {submitting ? '送信中…' : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/handling')}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
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
