import { API_PATHS } from '../../config/api'
import { ApiError, apiClient } from '../../lib/api-client'
import type {
  Booking,
  BookingList,
  BookingRequest,
  CargoType,
  DuplicateShipper,
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


export function fetchLocations(): Promise<LocationOption[]> {
  return apiClient.get<LocationOption[]>(API_PATHS.bookingLocations)
}

export function searchBookings(type: CargoType | '', keyword: string): Promise<BookingList> {
  const params = new URLSearchParams()
  if (type !== '') {
    params.set('type', type)
  }
  if (keyword.trim() !== '') {
    params.set('keyword', keyword.trim())
  }
  const query = params.toString()
  return apiClient.get<BookingList>(
    query === '' ? API_PATHS.bookings : `${API_PATHS.bookings}?${query}`,
  )
}

export function bookCargo(request: BookingRequest): Promise<Booking> {
  return apiClient.post<Booking>(API_PATHS.bookings, request)
}
