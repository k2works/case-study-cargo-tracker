/**
 * 輸送見積のモック（US01・UC01）。
 *
 * <p><strong>本物と同じ式で計算する。</strong>概算料金は精算のモックが持つ式
 * （`billing.ts`）をそのまま使う——モックだけが違う数字を返すと、画面は「動く」まま
 * 本番で別の金額を出す（[ADR-028] 決定 6）。
 *
 * <p>写した本物の規則:
 * <ul>
 *   <li>受入基準 01-3 — 候補ごとに<strong>4 項目</strong>（経由港・所要日数・概算料金・航海番号）
 *   <li>受入基準 01-5 — <strong>「候補が 0 件」と「間に合う候補が 0 件」を区別する</strong>
 *   <li>[ADR-028] 決定 7 — 識別子は UUID、見積番号は `EST-YYYY` + 6 桁
 * </ul>
 */
import { HttpResponse, http } from 'msw'

import { API_PATHS } from '../../config/api'
import { LOCATIONS } from '../data'
import { estimateBaseAmount } from './billing'
import { findMockRoutes, voyages } from '../routes'

type MockRouteCandidate = {
  voyageNumber: string
  transitPort: string | null
  transitDays: number
  estimatedCost: number
}

type MockEstimate = {
  estimateId: string
  estimateNumber: string
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: string
  weightKg: number
  status: string
  statusLabel: string
  candidates: MockRouteCandidate[]
}

/** 作成した見積。**確定操作でだけ増える。** */
export const estimates: MockEstimate[] = []

let estimateSequence = 0

/** 地点の地域区分。**知らない港は断る**——既定値に倒すと、その港だけ安く見積もられる。 */
function regionOf(unLocode: string) {
  const location = LOCATIONS.find((item) => item.unLocode === unLocode)
  if (location === undefined) {
    throw new Error(`扱いを決めていない地点です: ${unLocode}`)
  }
  return location.region
}

/** 日数（暦）。荷主に「何日で着くか」を答えるための数字である。 */
function daysBetween(from: string, to: string) {
  return Math.floor(
    (new Date(to).getTime() - new Date(from).getTime()) / (24 * 60 * 60 * 1000),
  )
}

type QuoteRequest = {
  originUnLocode: string
  destinationUnLocode: string
  arrivalDeadline: string
  cargoType: string
  weightKg: number
}

/**
 * 候補を探して試算する。
 *
 * **期限では刈らない**——間に合う候補が無いときに「最短でも N 日超過」を言うには、
 * 間に合わない候補も受け取る必要がある（受入基準 01-5）。
 */
function quoteOf(request: QuoteRequest) {
  const found = findMockRoutes(
    voyages,
    request.originUnLocode,
    request.destinationUnLocode,
    request.arrivalDeadline,
    2,
    null,
    false,
  )

  const candidates: MockRouteCandidate[] = []
  let daysExceeded: number | null = null

  for (const legs of found) {
    const arrival = legs[legs.length - 1].arrivalTime
    const arrivalDate = arrival.slice(0, 10)
    if (arrivalDate > request.arrivalDeadline) {
      const exceeded = daysBetween(request.arrivalDeadline, arrivalDate)
      if (daysExceeded === null || exceeded < daysExceeded) {
        daysExceeded = exceeded
      }
      continue
    }
    candidates.push({
      voyageNumber: legs[0].voyageNumber,
      transitPort: legs.length <= 1 ? null : legs[0].toUnLocode,
      transitDays: daysBetween(legs[0].departureTime, arrival),
      // **式は精算と同じ 1 か所にある**（[ADR-028] 決定 6）
      estimatedCost: estimateBaseAmount(
        legs.map((leg) => ({
          loadRegion: regionOf(leg.fromUnLocode),
          unloadRegion: regionOf(leg.toUnLocode),
        })),
        request.weightKg,
        request.cargoType,
      ),
    })
  }

  return {
    candidates,
    daysExceeded: candidates.length === 0 ? daysExceeded : null,
  }
}

export const estimateHandlers = [
  http.get(API_PATHS.estimates, () => HttpResponse.json(estimates)),

  /** **`/:estimateId` より先に置く**——あとに置くと `quotes` が識別子として読まれる。 */
  http.post(API_PATHS.estimateQuotes, async ({ request }) => {
    const body = (await request.json()) as QuoteRequest
    return HttpResponse.json(quoteOf(body))
  }),

  http.post(API_PATHS.estimates, async ({ request }) => {
    const body = (await request.json()) as QuoteRequest
    const quote = quoteOf(body)
    estimateSequence += 1
    const estimate: MockEstimate = {
      estimateId: crypto.randomUUID(),
      estimateNumber: `EST-2026${String(estimateSequence).padStart(6, '0')}`,
      originUnLocode: body.originUnLocode,
      destinationUnLocode: body.destinationUnLocode,
      arrivalDeadline: body.arrivalDeadline,
      cargoType: body.cargoType,
      weightKg: body.weightKg,
      status: 'CREATED',
      statusLabel: '作成済',
      candidates: quote.candidates,
    }
    estimates.unshift(estimate)
    return HttpResponse.json(estimate, { status: 201 })
  }),

  http.get('/api/v1/estimates/:estimateId', ({ params }) => {
    const found = estimates.find((estimate) => estimate.estimateId === params.estimateId)
    if (found === undefined) {
      return HttpResponse.json({ message: '見積が見つかりません' }, { status: 404 })
    }
    return HttpResponse.json(found)
  }),
]
