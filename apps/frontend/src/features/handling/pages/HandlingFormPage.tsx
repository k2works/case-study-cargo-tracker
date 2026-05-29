import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  handlingTypeLabel,
  registerHandling,
  requiresClaimVerification,
  requiresVoyageNumber,
  type HandlingType,
} from '../api/handlingApi';
import { useAuth } from '../../auth/contexts/AuthContext';

const HANDLING_TYPES: HandlingType[] = ['RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS'];

/**
 * 連続入力モード（H4）の localStorage キー。
 * 荷役作業員が同じ航海・場所・作業員 ID で複数貨物を連続記録する典型シナリオに対応する。
 * 直前値を保持し次回フォーム表示時に初期値として復元する。
 */
const LAST_UNLOCODE_KEY = 'handling_last_unlocode';
const LAST_VOYAGE_KEY = 'handling_last_voyage';
const LAST_HANDLER_ID_KEY = 'handling_last_handler_id';

function loadLast(key: string): string {
  try {
    return localStorage.getItem(key) ?? '';
  } catch {
    return '';
  }
}

function saveLast(key: string, value: string) {
  try {
    if (value) localStorage.setItem(key, value);
  } catch {
    // ignore
  }
}

export default function HandlingFormPage() {
  const navigate = useNavigate();
  const auth = useAuth();
  const [trackingNumber, setTrackingNumber] = useState('');
  const [handlingType, setHandlingType] = useState<HandlingType>('RECEIVE');
  // H4: 連続入力モード - 直前値を初期値として復元
  const [unlocode, setUnlocode] = useState(() => loadLast(LAST_UNLOCODE_KEY));
  const [voyageNumber, setVoyageNumber] = useState(() => loadLast(LAST_VOYAGE_KEY));
  const [occurredAt, setOccurredAt] = useState(() => defaultOccurredAt());
  // H4: 作業員 ID はログイン中のユーザー名で初期化（毎回手入力させない）
  const [handlerId, setHandlerId] = useState(
    () => loadLast(LAST_HANDLER_ID_KEY) || auth.username || '',
  );
  const [consigneeName, setConsigneeName] = useState('');
  const [signatureRef, setSignatureRef] = useState('');
  const [confirmationCode, setConfirmationCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const claimRequired = requiresClaimVerification(handlingType);
  const voyageRequired = requiresVoyageNumber(handlingType);

  // React.FormEvent は型定義上 @deprecated のため、最小構造的型を inline で記述する。
  async function handleSubmit(ev: { preventDefault: () => void }) {
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
      // H4: 連続入力モード - 同じ航海・場所・作業員 ID で複数貨物を続けて記録できるよう
      // 直前値を localStorage に永続化する
      saveLast(LAST_UNLOCODE_KEY, unlocode);
      saveLast(LAST_VOYAGE_KEY, voyageNumber);
      saveLast(LAST_HANDLER_ID_KEY, handlerId);
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
          <span>追跡番号</span>
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
          <span>作業種別</span>
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
          <span>作業場所（UN/LOCODE）</span>
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
          <span>作業日時</span>
          <input
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
            required
            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
          />
        </label>

        <label className="block text-sm font-medium text-gray-700">
          <span>航海番号 {voyageRequired && <span className="text-red-600">*</span>}</span>
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
          <span>作業員 ID</span>
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
                <span>荷受人氏名 <span className="text-red-600">*</span></span>
                <input
                  type="text"
                  value={consigneeName}
                  onChange={(e) => setConsigneeName(e.target.value)}
                  required
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <label className="block text-sm font-medium text-gray-700">
                <span>署名参照（URL / ID）</span>
                <input
                  type="text"
                  value={signatureRef}
                  onChange={(e) => setSignatureRef(e.target.value)}
                  placeholder="https://... or sign-12345"
                  className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
                />
              </label>
              <label className="block text-sm font-medium text-gray-700">
                <span>確認コード</span>
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
