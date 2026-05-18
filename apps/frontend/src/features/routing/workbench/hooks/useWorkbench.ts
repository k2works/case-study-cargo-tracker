import { useMutation, useQueryClient } from '@tanstack/react-query'
import { routingApiClient, bookingApiClient } from '../../../../lib/api-client'
import type {
  RouteCandidatesResponse,
  SelectRouteRequest,
  SelectedRouteResponse,
  AssignRouteRequest,
  AdjustRouteRequest,
  AdjustRouteResponse,
} from '../types/workbench'
import type { BookingResponse } from '../../../booking/types/booking'

// 経路候補算出（US08）: ステートレスなので useState + fetch で管理
export async function fetchRouteCandidates(
  origin: string,
  destination: string,
  arrivalDeadline: string,
  cargoType: string,
): Promise<RouteCandidatesResponse> {
  const params = new URLSearchParams({ origin, destination, arrivalDeadline, cargoType })
  return routingApiClient.get<RouteCandidatesResponse>(`/api/v1/routing/candidates?${params}`)
}

// 経路条件調整・再算出（US10）
export async function adjustRouteCandidates(req: AdjustRouteRequest): Promise<AdjustRouteResponse> {
  return routingApiClient.post<AdjustRouteResponse>('/api/v1/routing/adjust', req)
}

// 経路選択（US09）: 候補インデックスを指定して Legs を取得
export async function selectRoute(req: SelectRouteRequest): Promise<SelectedRouteResponse> {
  return routingApiClient.post<SelectedRouteResponse>('/api/v1/routing/select', req)
}

// 経路紐付け（US11）: bookingms に assign-route を送信
export function useAssignRoute(bookingId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: AssignRouteRequest) =>
      bookingApiClient.post<BookingResponse>(
        `/api/v1/bookings/${bookingId}/assign-route`,
        req,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}
