import { useQuery } from '@tanstack/react-query'
import { env } from '../../../config/env'
import type { TrackingFetchError, TrackingInfo, TrackingErrorCode } from '../types/tracking'

/**
 * 公開追跡照会 API（US18）。
 *
 * 認証ストアにアクセスせず、`Authorization` ヘッダーも付けない。
 * トークン期限切れ・改ざんなどは {@link TrackingFetchError} としてキャッチ側で扱う。
 */
async function fetchTrackingInfo(
  trackingNumber: string,
  token: string,
): Promise<TrackingInfo> {
  const url = `${env.trackingApiBaseUrl}/api/v1/tracking/${encodeURIComponent(trackingNumber)}?token=${encodeURIComponent(token)}`
  const response = await fetch(url, {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    const body: { errorCode?: TrackingErrorCode; message?: string } = await response
      .json()
      .catch(() => ({}))
    const error: TrackingFetchError = {
      status: response.status,
      errorCode: body.errorCode ?? null,
      message: body.message ?? 'トラッキング情報の取得に失敗しました',
    }
    throw error
  }

  return response.json() as Promise<TrackingInfo>
}

/**
 * 公開追跡照会フック（US18 S15 画面用）。
 *
 * トークンと追跡番号が両方そろっている場合のみフェッチする。
 * 401/403/404 は React Query の `error` として返却される。
 */
export function useTrackingInfo(trackingNumber: string | undefined, token: string | null) {
  return useQuery<TrackingInfo, TrackingFetchError>({
    queryKey: ['tracking', trackingNumber, token],
    queryFn: () => fetchTrackingInfo(trackingNumber as string, token as string),
    enabled: !!trackingNumber && !!token,
    retry: false,
  })
}
