import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useRegisterHandlingActivity } from '../hooks/useHandling'
import { HANDLING_TYPE_LABELS, type HandlingType, type RegisterHandlingActivityRequest } from '../types/handling'

interface FormState {
  trackingNumber: string
  handlingType: HandlingType
  unlocode: string
  occurredAt: string
  voyageNumber: string
  operatorId: string
  consigneeName: string
  confirmationCode: string
  signatureRef: string
  verificationMethod: 'code' | 'signature'
}

const INITIAL_STATE: FormState = {
  trackingNumber: '',
  handlingType: 'RECEIVE',
  unlocode: '',
  occurredAt: '',
  voyageNumber: '',
  operatorId: '',
  consigneeName: '',
  confirmationCode: '',
  signatureRef: '',
  verificationMethod: 'code',
}

export function HandlingActivityForm() {
  const navigate = useNavigate()
  const [form, setForm] = useState<FormState>(INITIAL_STATE)
  const [error, setError] = useState<string | null>(null)
  const register = useRegisterHandlingActivity()

  const requiresVoyageNumber = form.handlingType === 'LOAD' || form.handlingType === 'UNLOAD'
  const requiresClaimVerification = form.handlingType === 'CLAIM'

  const handleSubmit = (e: { preventDefault(): void }) => {
    e.preventDefault()
    setError(null)

    const request: RegisterHandlingActivityRequest = {
      trackingNumber: form.trackingNumber,
      handlingType: form.handlingType,
      unlocode: form.unlocode.toUpperCase(),
      occurredAt: form.occurredAt,
      operatorId: form.operatorId,
      ...(requiresVoyageNumber ? { voyageNumber: form.voyageNumber } : {}),
      ...(requiresClaimVerification
        ? {
            claimVerification: {
              consigneeName: form.consigneeName,
              ...(form.verificationMethod === 'code'
                ? { confirmationCode: form.confirmationCode }
                : { signatureRef: form.signatureRef }),
            },
          }
        : {}),
    }

    register.mutate(request, {
      onSuccess: () => {
        navigate('/handling')
      },
      onError: (err: unknown) => {
        if (err instanceof Error) {
          setError(err.message)
        } else {
          setError('登録に失敗しました')
        }
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" data-testid="handling-activity-form">
      <div>
        <label htmlFor="handling-tracking-number" className="block text-sm font-medium text-gray-700">
          追跡番号
        </label>
        <input
          id="handling-tracking-number"
          type="text"
          value={form.trackingNumber}
          onChange={(e) => setForm({ ...form, trackingNumber: e.target.value })}
          required
          placeholder="TRK-YYYYMMDD-XXXXXXXX"
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          data-testid="handling-tracking-number-input"
        />
      </div>

      <div>
        <label htmlFor="handling-type" className="block text-sm font-medium text-gray-700">
          作業種別
        </label>
        <select
          id="handling-type"
          value={form.handlingType}
          onChange={(e) => setForm({ ...form, handlingType: e.target.value as HandlingType })}
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          data-testid="handling-type-select"
        >
          {(Object.keys(HANDLING_TYPE_LABELS) as HandlingType[]).map((type) => (
            <option key={type} value={type}>
              {HANDLING_TYPE_LABELS[type]}（{type}）
            </option>
          ))}
        </select>
      </div>

      <div>
        <label htmlFor="handling-unlocode" className="block text-sm font-medium text-gray-700">
          作業場所（UN/LOCODE）
        </label>
        <input
          id="handling-unlocode"
          type="text"
          value={form.unlocode}
          onChange={(e) => setForm({ ...form, unlocode: e.target.value })}
          required
          maxLength={5}
          placeholder="JPTYO"
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm uppercase"
          data-testid="handling-unlocode-input"
        />
      </div>

      <div>
        <label htmlFor="handling-occurred-at" className="block text-sm font-medium text-gray-700">
          作業日時
        </label>
        <input
          id="handling-occurred-at"
          type="datetime-local"
          value={form.occurredAt}
          onChange={(e) => setForm({ ...form, occurredAt: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          data-testid="handling-occurred-at-input"
        />
      </div>

      <div>
        <label htmlFor="handling-operator" className="block text-sm font-medium text-gray-700">
          作業員 ID
        </label>
        <input
          id="handling-operator"
          type="text"
          value={form.operatorId}
          onChange={(e) => setForm({ ...form, operatorId: e.target.value })}
          required
          className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
          data-testid="handling-operator-input"
        />
      </div>

      {requiresVoyageNumber && (
        <div data-testid="handling-voyage-section">
          <label htmlFor="handling-voyage" className="block text-sm font-medium text-gray-700">
            航海番号（{form.handlingType === 'LOAD' ? '積込' : '荷降し'}時必須）
          </label>
          <input
            id="handling-voyage"
            type="text"
            value={form.voyageNumber}
            onChange={(e) => setForm({ ...form, voyageNumber: e.target.value })}
            required={requiresVoyageNumber}
            placeholder="V-MOL-001"
            className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
            data-testid="handling-voyage-input"
          />
        </div>
      )}

      {requiresClaimVerification && (
        <fieldset className="border border-gray-200 rounded-md p-4 space-y-3" data-testid="handling-claim-section">
          <legend className="text-sm font-medium text-gray-700 px-2">荷受人確認（US16）</legend>
          <div>
            <label htmlFor="claim-consignee" className="block text-sm font-medium text-gray-700">
              荷受人氏名
            </label>
            <input
              id="claim-consignee"
              type="text"
              value={form.consigneeName}
              onChange={(e) => setForm({ ...form, consigneeName: e.target.value })}
              required={requiresClaimVerification}
              className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
              data-testid="claim-consignee-input"
            />
          </div>
          <div>
            <span className="block text-sm font-medium text-gray-700">確認方法</span>
            <label className="inline-flex items-center mr-4">
              <input
                type="radio"
                value="code"
                checked={form.verificationMethod === 'code'}
                onChange={() => setForm({ ...form, verificationMethod: 'code' })}
                data-testid="claim-method-code"
              />
              <span className="ml-2">確認コード</span>
            </label>
            <label className="inline-flex items-center">
              <input
                type="radio"
                value="signature"
                checked={form.verificationMethod === 'signature'}
                onChange={() => setForm({ ...form, verificationMethod: 'signature' })}
                data-testid="claim-method-signature"
              />
              <span className="ml-2">署名画像</span>
            </label>
          </div>
          {form.verificationMethod === 'code' ? (
            <div>
              <label htmlFor="claim-code" className="block text-sm font-medium text-gray-700">
                確認コード
              </label>
              <input
                id="claim-code"
                type="text"
                value={form.confirmationCode}
                onChange={(e) => setForm({ ...form, confirmationCode: e.target.value })}
                required
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
                data-testid="claim-code-input"
              />
            </div>
          ) : (
            <div>
              <label htmlFor="claim-signature" className="block text-sm font-medium text-gray-700">
                署名画像 URI
              </label>
              <input
                id="claim-signature"
                type="text"
                value={form.signatureRef}
                onChange={(e) => setForm({ ...form, signatureRef: e.target.value })}
                required
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2 text-sm"
                data-testid="claim-signature-input"
              />
            </div>
          )}
        </fieldset>
      )}

      {error && (
        <div
          className="rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700"
          role="alert"
          data-testid="handling-error"
        >
          {error}
        </div>
      )}

      <div className="flex space-x-2">
        <button
          type="submit"
          disabled={register.isPending}
          className="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm disabled:opacity-50"
          data-testid="handling-submit"
        >
          {register.isPending ? '登録中...' : '登録'}
        </button>
        <button
          type="button"
          onClick={() => navigate('/handling')}
          className="bg-gray-200 text-gray-800 px-4 py-2 rounded-md text-sm"
        >
          キャンセル
        </button>
      </div>
    </form>
  )
}
