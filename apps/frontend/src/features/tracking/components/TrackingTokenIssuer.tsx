import { useState } from 'react'
import { useIssueTrackingToken } from '../hooks/useTracking'

type Props = {
  trackingNumber: string
  /** 表示用に通知先メールアドレス（任意） */
  recipientEmail?: string | null
  /** 再発行ボタン用に「再発行」ラベルを使うか */
  variant?: 'issue' | 'reissue'
  testId?: string
}

/**
 * S10 / S17 から呼ばれる「追跡トークン発行（メール送信）」セクション（US18 受入 5）。
 *
 * - POST /api/v1/tracking/_internal/issue-token を呼び出す
 * - 成功時に URL + 有効期限を alert-success で表示
 * - 「URL をクリップボードにコピー」ボタンも提供する
 */
export function TrackingTokenIssuer({
  trackingNumber,
  recipientEmail,
  variant = 'issue',
  testId,
}: Props) {
  const issueToken = useIssueTrackingToken()
  const [copied, setCopied] = useState(false)
  const [issuedUrl, setIssuedUrl] = useState<string | null>(null)
  const [validUntil, setValidUntil] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleIssue = () => {
    setErrorMessage(null)
    setCopied(false)
    issueToken.mutate(trackingNumber, {
      onSuccess: (response) => {
        const publicUrl = `${window.location.origin}${response.url}`
        setIssuedUrl(publicUrl)
        setValidUntil(response.validUntil)
      },
      onError: (e) => setErrorMessage(e.message),
    })
  }

  const handleCopy = async () => {
    if (!issuedUrl) return
    await navigator.clipboard.writeText(issuedUrl)
    setCopied(true)
  }

  const labels = {
    issue: {
      heading: '荷主への追跡情報通知',
      button: '追跡トークンを発行してメール送信',
    },
    reissue: {
      heading: '追跡トークン再発行',
      button: '追跡トークンを再発行',
    },
  } as const

  return (
    <section
      className="mt-6 rounded border border-gray-200 bg-white p-4"
      data-testid={testId ?? 'tracking-token-issuer'}
    >
      <h2 className="text-sm font-bold mb-2">{labels[variant].heading}</h2>
      <dl className="grid grid-cols-3 gap-x-4 gap-y-1 text-sm mb-3">
        {recipientEmail !== undefined && (
          <>
            <dt className="text-gray-600">メール送信先</dt>
            <dd className="col-span-2">{recipientEmail ?? '（未設定）'}</dd>
          </>
        )}
        <dt className="text-gray-600">リンク有効期限</dt>
        <dd className="col-span-2">発行から 30 日（配送完了後 7 日で自動失効）</dd>
      </dl>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={handleIssue}
          disabled={issueToken.isPending}
          data-testid="issue-tracking-token-button"
          className="rounded bg-purple-600 px-4 py-2 text-sm text-white hover:bg-purple-700 disabled:opacity-50"
        >
          {issueToken.isPending ? '発行中…' : labels[variant].button}
        </button>
        {issuedUrl && (
          <button
            type="button"
            onClick={handleCopy}
            data-testid="copy-tracking-url-button"
            className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
          >
            {copied ? 'コピーしました' : 'URL をクリップボードにコピー'}
          </button>
        )}
      </div>
      {errorMessage && (
        <div
          className="mt-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
          role="alert"
          data-testid="tracking-token-error"
        >
          {errorMessage}
        </div>
      )}
      {issuedUrl && validUntil && (
        <div
          className="mt-3 rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800"
          role="status"
          data-testid="tracking-token-success"
        >
          <p className="font-bold">✓ 追跡トークン URL を発行しました</p>
          <p className="break-all font-mono text-xs mt-1">URL: {issuedUrl}</p>
          <p className="text-xs mt-1">有効期限: {validUntil}</p>
        </div>
      )}
    </section>
  )
}
