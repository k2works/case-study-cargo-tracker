import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { env } from '../../../config/env'
import { useAuthStore } from '../../../stores/authStore'
import type {
  TrackingErrorCode,
  TrackingException,
  TrackingFetchError,
  TrackingInfo,
  TrackingListItem,
} from '../types/tracking'

export type IssueTrackingTokenResponse = {
  url: string
  token: string
  validUntil: string
}

export type InitializeTrackingRequest = {
  trackingNumber: string
  bookingId: string
  originUnlocode: string
  destinationUnlocode: string
  estimatedArrival: string
  voyageNumber: string
}

export type InitializeTrackingResponse = {
  trackingNumber: string
  status: 'initialized' | 'already_initialized'
}

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

/**
 * S16 追跡管理一覧（管理者認証必須）。
 *
 * gatewayms 側で `/api/v1/tracking` 完全一致は管理者 JWT 必須として処理される。
 */
export function useTrackingList() {
  return useQuery<TrackingListItem[]>({
    queryKey: ['tracking', 'list'],
    queryFn: async () => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(`${env.trackingApiBaseUrl}/api/v1/tracking`, {
        headers: {
          Accept: 'application/json',
          ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
        },
      })
      if (response.status === 401) {
        useAuthStore.getState().logout()
        throw new Error('Unauthorized')
      }
      if (!response.ok) {
        throw new Error('追跡情報一覧の取得に失敗しました')
      }
      return (await response.json()) as TrackingListItem[]
    },
  })
}

/**
 * IT6 暫定: trackingms 追跡集約初期化 mutation。
 *
 * bookingms の追跡番号発行後、Event 駆動 ACL が未実装のため、
 * フロント側から POST /api/v1/tracking/_internal/initialize を明示的に呼んで
 * trackingms 側の Read Model（tracking_summary）にも反映させる。
 *
 * 冪等性: trackingms 側で `exists` チェックがあり、既に初期化済みなら 200
 * `status=already_initialized` を返すため、複数回呼ばれても安全。
 *
 * TI06+ で CargoTrackedEvent 駆動 ACL が完成すれば本フックは不要になる。
 */
export function useInitializeTracking() {
  const queryClient = useQueryClient()
  return useMutation<InitializeTrackingResponse, Error, InitializeTrackingRequest>({
    mutationFn: async (request: InitializeTrackingRequest) => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(
        `${env.trackingApiBaseUrl}/api/v1/tracking/_internal/initialize`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
          },
          body: JSON.stringify(request),
        },
      )
      if (!response.ok) {
        const body: { message?: string } = await response.json().catch(() => ({}))
        throw new Error(body.message ?? '追跡集約の初期化に失敗しました')
      }
      return response.json() as Promise<InitializeTrackingResponse>
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracking', 'list'] })
    },
  })
}

export type RegisterTrackingExceptionRequest = {
  trackingNumber: string
  exceptionType: string
  description: string
  occurredUnlocode?: string
  occurredAt?: string
  operatorId?: string
}

export type RegisterTrackingExceptionResponse = {
  trackingNumber: string
  exceptionId: string
}

/**
 * US19 追跡例外登録 mutation。
 *
 * {@code POST /api/v1/tracking/{tn}/exceptions} を呼び出して例外を登録する。
 */
export function useRegisterTrackingException() {
  const queryClient = useQueryClient()
  return useMutation<
    RegisterTrackingExceptionResponse,
    Error,
    RegisterTrackingExceptionRequest
  >({
    mutationFn: async (request) => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(
        `${env.trackingApiBaseUrl}/api/v1/tracking/${encodeURIComponent(request.trackingNumber)}/exceptions`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
          },
          body: JSON.stringify({
            exceptionType: request.exceptionType,
            description: request.description,
            occurredUnlocode: request.occurredUnlocode,
            occurredAt: request.occurredAt,
            operatorId: request.operatorId,
          }),
        },
      )
      if (!response.ok) {
        const body: { message?: string } = await response.json().catch(() => ({}))
        throw new Error(body.message ?? '例外の登録に失敗しました')
      }
      return response.json() as Promise<RegisterTrackingExceptionResponse>
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tracking', variables.trackingNumber] })
    },
  })
}

/**
 * 追跡例外一覧クエリ（US20 S19 画面用）。
 */
export function useTrackingExceptions(trackingNumber: string) {
  return useQuery<TrackingException[]>({
    queryKey: ['tracking', trackingNumber, 'exceptions'],
    queryFn: async () => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(
        `${env.trackingApiBaseUrl}/api/v1/tracking/${encodeURIComponent(trackingNumber)}/exceptions`,
        {
          headers: {
            Accept: 'application/json',
            ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
          },
        },
      )
      if (!response.ok) throw new Error('例外一覧の取得に失敗しました')
      return response.json() as Promise<TrackingException[]>
    },
    enabled: !!trackingNumber,
  })
}

export type ResolveTrackingExceptionRequest = {
  trackingNumber: string
  exceptionId: string
  resolution: string
  operatorId?: string
}

export type ResolveTrackingExceptionResponse = {
  trackingNumber: string
  exceptionId: string
}

/**
 * US20 例外解決 mutation。
 */
export function useResolveTrackingException() {
  const queryClient = useQueryClient()
  return useMutation<ResolveTrackingExceptionResponse, Error, ResolveTrackingExceptionRequest>({
    mutationFn: async (request) => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(
        `${env.trackingApiBaseUrl}/api/v1/tracking/${encodeURIComponent(request.trackingNumber)}/exceptions/${encodeURIComponent(request.exceptionId)}/resolve`,
        {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
          },
          body: JSON.stringify({
            resolution: request.resolution,
            operatorId: request.operatorId,
          }),
        },
      )
      if (!response.ok) {
        const body: { message?: string } = await response.json().catch(() => ({}))
        throw new Error(body.message ?? '例外の解決に失敗しました')
      }
      return response.json() as Promise<ResolveTrackingExceptionResponse>
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['tracking', variables.trackingNumber, 'exceptions'],
      })
    },
  })
}

/**
 * 管理者用 JWT 発行 mutation（US18 連携）。
 *
 * S10 予約詳細 / S17 追跡詳細管理から呼ばれ、
 * `POST /api/v1/tracking/_internal/issue-token` を実行する。
 * 認証必須のため `Authorization: Bearer <管理者 JWT>` ヘッダーを付与する。
 */
export function useIssueTrackingToken() {
  return useMutation<IssueTrackingTokenResponse, Error, string>({
    mutationFn: async (trackingNumber: string) => {
      const adminToken = useAuthStore.getState().token
      const response = await fetch(
        `${env.trackingApiBaseUrl}/api/v1/tracking/_internal/issue-token`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
          },
          body: JSON.stringify({ trackingNumber }),
        },
      )
      if (!response.ok) {
        const body: { message?: string } = await response.json().catch(() => ({}))
        throw new Error(body.message ?? 'トークン発行に失敗しました')
      }
      return response.json() as Promise<IssueTrackingTokenResponse>
    },
  })
}
