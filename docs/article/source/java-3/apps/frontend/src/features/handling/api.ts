import { API_PATHS } from '../../config/api'
import { apiClient } from '../../lib/api-client'
import type {
  HandlingActivity,
  HandlingActivityRequest,
  HandlingLocation,
  HandlingTypeChoice,
} from './types'

/**
 * 荷役の種別と、その要件（[ADR-023] 決定 1）。
 *
 * **画面が要件を持たない。** 「積込なら航海番号が要る」を画面に書くと、規則が
 * サーバと画面の 2 か所に分かれる。
 */
export function fetchHandlingTypes(): Promise<HandlingTypeChoice[]> {
  return apiClient.get<HandlingTypeChoice[]>(`${API_PATHS.handling}/types`)
}

/** 作業場所の選択肢（US15-3）。 */
export function fetchHandlingLocations(): Promise<HandlingLocation[]> {
  return apiClient.get<HandlingLocation[]>(`${API_PATHS.handling}/locations`)
}

/**
 * 1 つの貨物に何が起きたかを、時系列で取る。
 *
 * **追跡番号で引く。** 荷役作業員も追跡管理者も、手元にあるのは追跡番号である。
 * 予約番号でしか引けないと、「あの貨物はもう積んだか」という問い合わせに誰も答えられない。
 */
export function fetchHandlingHistory(trackingNumber: string): Promise<HandlingActivity[]> {
  return apiClient.get<HandlingActivity[]>(
    `${API_PATHS.handling}?trackingNumber=${encodeURIComponent(trackingNumber)}`,
  )
}

/** 荷役作業を記録する（US15・US16）。 */
export function registerHandlingActivity(
  request: HandlingActivityRequest,
): Promise<HandlingActivity> {
  return apiClient.post<HandlingActivity>(API_PATHS.handling, request)
}
