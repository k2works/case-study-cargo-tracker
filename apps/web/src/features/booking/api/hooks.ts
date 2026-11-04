import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/errors';
import { useApi } from '@/providers/ApiProvider';
import { useReflectionConfig } from '@/providers/ReflectionConfig';
import { BookingDocument, BookingsDocument, ShipperDocument, ShippersDocument } from './documents.query';
import { RegisterBookingDocument, RegisterShipperDocument } from './documents.command';
import type { RegisterBookingMutationVariables, RegisterShipperMutationVariables } from '@/types/gql-command/graphql';

/** feature の api 層：画面（components）は GraphQL を直接呼ばず、これらのフックを通す（F2）。 */

export function useShippers(keyword: string) {
  const api = useApi();
  return useQuery<Awaited<ReturnType<typeof fetchShippers>>, ApiError>({
    queryKey: ['shippers', keyword],
    queryFn: () => fetchShippers(api, keyword),
    placeholderData: keepPreviousData,
  });
}
const fetchShippers = async (api: ReturnType<typeof useApi>, keyword: string) =>
  (await api.query(ShippersDocument, { keyword: keyword.trim() || undefined })).shippers;

export function useShipper(id: string) {
  const api = useApi();
  return useQuery<Awaited<ReturnType<typeof fetchShipper>>, ApiError>({ queryKey: ['shipper', id], queryFn: () => fetchShipper(api, id) });
}
const fetchShipper = async (api: ReturnType<typeof useApi>, id: string) => (await api.query(ShipperDocument, { id })).shipper;

export type BookingFilter = { status?: string; shipperId?: string };

export function useBookings(filter: BookingFilter) {
  const api = useApi();
  return useQuery<Awaited<ReturnType<typeof fetchBookings>>, ApiError>({
    queryKey: ['bookings', filter],
    queryFn: () => fetchBookings(api, filter),
  });
}
const fetchBookings = async (api: ReturnType<typeof useApi>, filter: BookingFilter) =>
  (await api.query(BookingsDocument, { status: filter.status || undefined, shipperId: filter.shipperId || undefined })).bookings;

export function useBooking(id: string) {
  const api = useApi();
  return useQuery<Awaited<ReturnType<typeof fetchBooking>>, ApiError>({ queryKey: ['booking', id], queryFn: () => fetchBooking(api, id) });
}
const fetchBooking = async (api: ReturnType<typeof useApi>, id: string) => (await api.query(BookingDocument, { id })).booking;

export function useRegisterShipper() {
  const api = useApi();
  return useMutation<string, ApiError, RegisterShipperMutationVariables>({
    mutationFn: async (variables) => (await api.command(RegisterShipperDocument, variables)).registerShipper.shipperId,
  });
}

export type RegisteredBooking = { bookingId: string; number: string; version: number };

export function useRegisterBooking() {
  const api = useApi();
  return useMutation<RegisteredBooking, ApiError, RegisterBookingMutationVariables>({
    mutationFn: async (variables) => (await api.command(RegisterBookingDocument, variables)).registerBooking,
  });
}

/**
 * 結果整合への対応（FE 3.2）：応答の版以上になるまで、短い間隔から始めて徐々に間隔を延ばして再取得する。
 * 上限を過ぎても追いつかないときは、エラーにせず `timedOut` を返し、画面は「反映待ち」を示して手動の更新を許す。
 */
export function useAwaitReflection(target: { bookingId: string; version: number } | null) {
  const api = useApi();
  const { initialMs, maxMs, timeoutMs } = useReflectionConfig();
  const [timedOut, setTimedOut] = useState(false);
  const [attempt, setAttempt] = useState(0);

  const key = target ? `${target.bookingId}:${target.version}:${attempt}` : 'none';
  useEffect(() => {
    setTimedOut(false);
    if (!target) return;
    const timer = setTimeout(() => setTimedOut(true), timeoutMs);
    return () => clearTimeout(timer);
  }, [key, timeoutMs]);

  const query = useQuery({
    queryKey: ['reflection', target?.bookingId, target?.version, attempt],
    queryFn: async () => (await api.query(BookingsDocument, {})).bookings.find((b) => b.id === target!.bookingId) ?? null,
    enabled: target !== null && !timedOut,
    refetchInterval: (q) => {
      const found = q.state.data;
      if (found && found.version >= target!.version) return false;
      // 500ms から始めて徐々に延ばす（上限 2s）。失敗した回数に応じて指数的に
      const n = q.state.dataUpdateCount + q.state.fetchFailureCount;
      return Math.min(maxMs, initialMs * 2 ** Math.min(n, 4));
    },
  });

  const reflected = !!query.data && !!target && query.data.version >= target.version;
  return { reflected, timedOut: timedOut && !reflected, retry: () => setAttempt((a) => a + 1) };
}
