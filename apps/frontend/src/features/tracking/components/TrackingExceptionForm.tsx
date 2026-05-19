import { useState } from 'react'
import { useRegisterTrackingException } from '../hooks/useTracking'

type Props = {
  trackingNumber: string
  onSuccess?: (exceptionId: string) => void
}

export function TrackingExceptionForm({ trackingNumber, onSuccess }: Props) {
  const [exceptionType, setExceptionType] = useState('')
  const [description, setDescription] = useState('')
  const [occurredUnlocode, setOccurredUnlocode] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { mutate, isPending, isError, error } = useRegisterTrackingException()

  function validate() {
    const newErrors: Record<string, string> = {}
    if (!exceptionType) newErrors.exceptionType = '例外種別を選択してください'
    if (!description.trim()) newErrors.description = '説明を入力してください'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return

    mutate(
      {
        trackingNumber,
        exceptionType,
        description,
        occurredUnlocode: occurredUnlocode || undefined,
      },
      {
        onSuccess: (data) => {
          onSuccess?.(data.exceptionId)
        },
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} aria-label="追跡例外登録フォーム">
      <div>
        <label htmlFor="exceptionType">例外種別</label>
        <select
          id="exceptionType"
          value={exceptionType}
          onChange={(e) => setExceptionType(e.target.value)}
          aria-label="例外種別"
        >
          <option value="">選択してください</option>
          <option value="DELAY">遅延（DELAY）</option>
          <option value="DAMAGE">破損（DAMAGE）</option>
          <option value="LOSS">紛失（LOSS）</option>
        </select>
        {errors.exceptionType && <p role="alert">{errors.exceptionType}</p>}
      </div>

      {exceptionType === 'LOSS' && (
        <div role="alert" aria-label="緊急通知バナー">
          紛失（LOSS）が選択されました。緊急通知が関係者に送信されます。
        </div>
      )}

      <div>
        <label htmlFor="occurredUnlocode">発生場所（UN/LOCODE）</label>
        <input
          id="occurredUnlocode"
          type="text"
          value={occurredUnlocode}
          onChange={(e) => setOccurredUnlocode(e.target.value)}
          placeholder="例: JPTYO"
          aria-label="発生場所"
        />
      </div>

      <div>
        <label htmlFor="description">説明</label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          aria-label="説明"
          rows={4}
        />
        {errors.description && <p role="alert">{errors.description}</p>}
      </div>

      {isError && <p role="alert">{error?.message ?? '登録に失敗しました'}</p>}

      <button type="submit" disabled={isPending}>
        {isPending ? '登録中...' : '登録'}
      </button>
    </form>
  )
}
