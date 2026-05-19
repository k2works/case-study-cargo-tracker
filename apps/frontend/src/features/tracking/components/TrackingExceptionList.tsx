import { useState } from 'react'
import { useTrackingExceptions, useResolveTrackingException } from '../hooks/useTracking'

type Props = {
  trackingNumber: string
}

export function TrackingExceptionList({ trackingNumber }: Props) {
  const { data: exceptions, isLoading, isError } = useTrackingExceptions(trackingNumber)
  const { mutate: resolve, isPending } = useResolveTrackingException()
  const [resolutionInputs, setResolutionInputs] = useState<Record<string, string>>({})

  if (isLoading) return <p>読み込み中...</p>
  if (isError) return <p role="alert">例外一覧の取得に失敗しました</p>
  if (!exceptions || exceptions.length === 0) return <p>例外はありません</p>

  return (
    <ul aria-label="追跡例外一覧">
      {exceptions.map((exc) => (
        <li key={exc.exceptionId}>
          <div>
            <span>{exc.exceptionType}</span>
            {exc.escalated && <span>緊急</span>}
            <span>{exc.responseStatus}</span>
          </div>
          <p>{exc.description}</p>
          {exc.occurredUnlocode && <p>{exc.occurredUnlocode}</p>}
          {exc.occurredAt && <p>{exc.occurredAt}</p>}
          {exc.resolution && <p>解決内容: {exc.resolution}</p>}
          {exc.responseStatus === 'PENDING' && (
            <div>
              <input
                type="text"
                placeholder="解決内容を入力"
                aria-label="解決内容"
                value={resolutionInputs[exc.exceptionId] ?? ''}
                onChange={(e) =>
                  setResolutionInputs((prev) => ({ ...prev, [exc.exceptionId]: e.target.value }))
                }
              />
              <button
                type="button"
                disabled={isPending}
                onClick={() => {
                  resolve({
                    trackingNumber,
                    exceptionId: exc.exceptionId,
                    resolution: resolutionInputs[exc.exceptionId] ?? '',
                  })
                }}
              >
                解決
              </button>
            </div>
          )}
        </li>
      ))}
    </ul>
  )
}
