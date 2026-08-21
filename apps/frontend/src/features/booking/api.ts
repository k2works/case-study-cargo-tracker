import { API_PATHS } from '../../config/api'
import { ApiError, apiClient } from '../../lib/api-client'
import type {
  Booking,
  BookingList,
  BookingRequest,
  CargoType,
  DuplicateShipper,
  HazardClassOption,
  LocationOption,
  Shipper,
  ShipperRequest,
} from './types'

export type RegistrationOutcome =
  | { kind: 'registered'; shipper: Shipper }
  | { kind: 'duplicate'; duplicate: DuplicateShipper }

export function searchShippers(keyword: string): Promise<Shipper[]> {
  const query = keyword.trim() === '' ? '' : `?keyword=${encodeURIComponent(keyword.trim())}`
  return apiClient.get<Shipper[]>(`${API_PATHS.shippers}${query}`)
}

/**
 * 荷主を登録する。
 *
 * 重複（409）は失敗ではなく、既存を使うか新規で登録するかを利用者に選ばせるための応答である。
 * 例外として投げると、呼び出し側が「登録できなかった」としか扱えなくなる。
 */
export async function registerShipper(request: ShipperRequest): Promise<RegistrationOutcome> {
  try {
    const shipper = await apiClient.post<Shipper>(API_PATHS.shippers, request)
    return { kind: 'registered', shipper }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && error.body !== undefined) {
      return { kind: 'duplicate', duplicate: error.body as DuplicateShipper }
    }
    throw error
  }
}


/** 荷主 1 件。編集画面を URL で直接開いた（再読み込みした）ときの復元に使う。 */
export function fetchShipper(id: number): Promise<Shipper> {
  return apiClient.get<Shipper>(`${API_PATHS.shippers}/${id}`)
}

/**
 * 登録済みの荷主を直す（US02 / #550）。
 *
 * 重複（409）の問いかけは無い。編集はすでにどの荷主かが分かっているため、
 * 「同じお客様かもしれない」という判断が要らない。
 */
export function editShipper(id: number, request: ShipperRequest): Promise<Shipper> {
  return apiClient.put<Shipper>(`${API_PATHS.shippers}/${id}`, request)
}

export function fetchLocations(): Promise<LocationOption[]> {
  return apiClient.get<LocationOption[]>(API_PATHS.bookingLocations)
}

export function fetchHazardClasses(): Promise<HazardClassOption[]> {
  return apiClient.get<HazardClassOption[]>(API_PATHS.bookingHazardClasses)
}

export function searchBookings(
  type: CargoType | '',
  keyword: string,
  routingStatus = '',
): Promise<BookingList> {
  const params = new URLSearchParams()
  if (type !== '') {
    params.set('type', type)
  }
  if (keyword.trim() !== '') {
    params.set('keyword', keyword.trim())
  }
  if (routingStatus !== '') {
    params.set('routingStatus', routingStatus)
  }
  const query = params.toString()
  return apiClient.get<BookingList>(
    query === '' ? API_PATHS.bookings : `${API_PATHS.bookings}?${query}`,
  )
}

/** 予約 1 件。画面の URL に出るのは予約番号であり、内部の id ではない。 */
export function fetchBooking(bookingId: string): Promise<Booking> {
  return apiClient.get<Booking>(`${API_PATHS.bookings}/${encodeURIComponent(bookingId)}`)
}

/** 経路設計を依頼する（US06）。 */
export function requestRouting(bookingId: string): Promise<Booking> {
  return apiClient.post<Booking>(
    `${API_PATHS.bookings}/${encodeURIComponent(bookingId)}/routing-request`,
    {},
  )
}

/**
 * 選んだ経路を予約に割り当てる（US09・[ADR-019]）。
 *
 * 候補の中身を丸ごと送る（候補 ID では参照しない。サーバは候補を保存していない）。
 * 地点は UN/LOCODE だけ送り、名称はサーバがマスタから引く。
 */
export function assignRoute(
  bookingId: string,
  legs: AssignRouteLeg[],
  maxTransshipments: number,
): Promise<Booking> {
  return apiClient.put<Booking>(
    `${API_PATHS.bookings}/${encodeURIComponent(bookingId)}/route`,
    { legs, maxTransshipments },
  )
}

export type AssignRouteLeg = {
  voyageNumber: string
  loadUnLocode: string
  unloadUnLocode: string
  loadTime: string
  unloadTime: string
}

/**
 * 条件では経路が組めないことを営業へ差し戻す（US10・[ADR-020] 決定 7）。
 *
 * 通知の仕組みが無いため、US06 と同じ形（状態で気づかせる）で代替する。
 */
export function requestConsultation(bookingId: string): Promise<Booking> {
  return apiClient.post<Booking>(
    `${API_PATHS.bookings}/${encodeURIComponent(bookingId)}/consultation-request`,
    {},
  )
}

export function bookCargo(request: BookingRequest): Promise<Booking> {
  return apiClient.post<Booking>(API_PATHS.bookings, request)
}
