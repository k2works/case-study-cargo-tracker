import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchAdvanceableStatuses,
  fetchExceptionTypes,
  fetchManagedTracking,
  fetchOpenExceptionList,
  fetchOpenExceptions,
  fetchPublicTracking,
  fetchShipperTracking,
  fetchShipperTrackingDetail,
  raiseTrackingException,
  resolveTrackingException,
  updateTrackingStatus,
} from "./api";
import type {
  ManualUpdateRequest,
  RaiseExceptionRequest,
  ResolveExceptionRequest,
} from "./types";

/**
 * 公開の追跡照会（US18）。
 *
 * **見つからないことは失敗ではない。** 打ち間違いが最も多く、再試行しても直らない。
 * 自動の再取得を切って、画面が理由をそのまま出せるようにする。
 */
export function usePublicTracking(trackingNumber: string | null) {
  return useQuery({
    queryKey: ["tracking", "public", trackingNumber],
    queryFn: () => fetchPublicTracking(trackingNumber as string),
    enabled: trackingNumber !== null && trackingNumber !== "",
    retry: false,
  });
}

export function useManagedTracking(trackingNumber: string | null) {
  return useQuery({
    queryKey: ["tracking", "manage", trackingNumber],
    queryFn: () => fetchManagedTracking(trackingNumber as string),
    enabled: trackingNumber !== null && trackingNumber !== "",
    retry: false,
  });
}

export function useShipperTracking() {
  return useQuery({
    queryKey: ["tracking", "shipper", "list"],
    queryFn: fetchShipperTracking,
    retry: false,
  });
}

export function useShipperTrackingDetail(trackingNumber: string | null) {
  return useQuery({
    queryKey: ["tracking", "shipper", trackingNumber],
    queryFn: () => fetchShipperTrackingDetail(trackingNumber as string),
    enabled: trackingNumber !== null && trackingNumber !== "",
    retry: false,
  });
}

/** その貨物から進められる状態。貨物が決まるまで引かない。 */
export function useAdvanceableStatuses(trackingNumber: string | null) {
  return useQuery({
    queryKey: ["tracking", "statuses", trackingNumber],
    queryFn: () => fetchAdvanceableStatuses(trackingNumber as string),
    enabled: trackingNumber !== null && trackingNumber !== "",
  });
}

export function useExceptionTypes() {
  return useQuery({
    queryKey: ["tracking", "exception-types"],
    queryFn: fetchExceptionTypes,
  });
}

export function useOpenExceptions() {
  return useQuery({
    queryKey: ["tracking", "exceptions", "open"],
    queryFn: fetchOpenExceptions,
  });
}

export function useOpenExceptionList() {
  return useQuery({
    queryKey: ["tracking", "exceptions", "list"],
    queryFn: fetchOpenExceptionList,
  });
}

/**
 * 追跡が動いたら、関わる問い合わせをまとめて取り直す。
 *
 * **未解決の件数も取り直す。** 起票したのに件数が変わらないと、追跡管理者は
 * 「登録できたのか」を判断できない。
 */
function useTrackingMutation<TRequest>(
  mutationFn: (request: TRequest) => Promise<unknown>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["tracking"] });
    },
  });
}

export function useUpdateTrackingStatus() {
  return useTrackingMutation<ManualUpdateRequest>(updateTrackingStatus);
}

export function useRaiseTrackingException() {
  return useTrackingMutation<RaiseExceptionRequest>(raiseTrackingException);
}

export function useResolveTrackingException() {
  return useTrackingMutation<ResolveExceptionRequest>(resolveTrackingException);
}
