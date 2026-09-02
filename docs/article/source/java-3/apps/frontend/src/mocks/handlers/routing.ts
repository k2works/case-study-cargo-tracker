/**
 * 航海スケジュールと経路候補のモック（US07・US08・US24・US25）。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { type MockVoyageRequest, LOCATIONS, differenceOf, toMockVoyage } from '../data'
import { findMockRoutes, toMockCandidate, voyages } from '../routes'
import { businessDateEndInstant, businessDateStartInstant } from '../../lib/business-time'

export const routingHandlers = [
  http.get(API_PATHS.voyageLocations, () =>
    HttpResponse.json(LOCATIONS.map(({ unLocode, name }) => ({ unLocode, name }))),
  ),

  http.get(API_PATHS.voyages, ({ request }) => {
    const params = new URL(request.url).searchParams
    const origin = params.get('origin')
    const destination = params.get('destination')
    const departureFrom = params.get('departureFrom')
    const departureTo = params.get('departureTo')
    const cargoType = params.get('cargoType')

    const matched = voyages.filter((voyage) => {
      const ports = [
        voyage.movements[0].departureUnLocode,
        ...voyage.movements.map((movement) => movement.arrivalUnLocode),
      ]
      // 寄港の順序で絞る。同じ港に寄ることと、その向きに運べることは別である
      if (origin !== null && destination !== null) {
        const from = ports.indexOf(origin)
        const to = ports.lastIndexOf(destination)
        if (from < 0 || to < 0 || from >= to) {
          return false
        }
      } else if (origin !== null && !ports.slice(0, -1).includes(origin)) {
        return false
      } else if (destination !== null && !ports.slice(1).includes(destination)) {
        return false
      }
      if (departureFrom !== null && voyage.departureTime < departureFrom) {
        return false
      }
      if (departureTo !== null && voyage.departureTime > departureTo) {
        return false
      }
      if (cargoType !== null && !voyage.supportedCargoTypes.includes(cargoType)) {
        return false
      }
      return true
    })

    const limit = 50
    return HttpResponse.json({
      voyages: matched.slice(0, limit),
      totalCount: matched.length,
      limit,
      truncated: matched.length > limit,
    })
  }),

  /**
   * 動作確認用の経路探索。
   *
   * **本物より甘くしない。** 期限を日付として業務タイムゾーンの当日終わりまでで判断し、
   * 貨物種別・積み替えの上限・積み替えの最低時間・推奨順・費用の式まで、サーバと同じ
   * 規則をなぞる。ここを緩めると、モックでだけ通る経路が画面に出て、実バックエンドで
   * 消える（IT3 で同じ形の欠陥が実バックエンドでだけ落ちた）。
   *
   * 規則そのものはサーバのドメインが正であり、ここは写し。実物との食い違いは
   * `real-backend.spec.ts` が捕まえる。
   */
  http.get(API_PATHS.routes, ({ request }) => {
    const params = new URL(request.url).searchParams
    const origin = params.get('origin') ?? ''
    const destination = params.get('destination') ?? ''
    const deadline = params.get('deadline') ?? ''
    const cargoType = params.get('cargoType') ?? 'GENERAL'
    const maxTransshipments = Number(params.get('maxTransshipments') ?? '2')

    // 本物が断る入力は、ここでも断る。通してしまうと「モックでは動くのに実物で 400」になる。
    // 認可（403）はここでは再現しない。ブラウザは利用者ヘッダを送らず、それを付けるのは
    // Gateway だからである。ロールの検査は実バックエンドの検査が受け持つ
    const originLocation = LOCATIONS.find((location) => location.unLocode === origin)
    const destinationLocation = LOCATIONS.find((location) => location.unLocode === destination)
    if (originLocation === undefined) {
      return HttpResponse.json({ message: '出発地が見つかりません' }, { status: 400 })
    }
    if (destinationLocation === undefined) {
      return HttpResponse.json({ message: '目的地が見つかりません' }, { status: 400 })
    }
    if (origin === destination) {
      return HttpResponse.json({ message: '出発地と目的地は同じにできません' }, { status: 400 })
    }
    if (deadline === '') {
      return HttpResponse.json({ message: '到着期限を指定してください' }, { status: 400 })
    }
    if (params.get('cargoType') === null) {
      return HttpResponse.json({ message: '貨物種別を指定してください' }, { status: 400 })
    }
    if (!Number.isInteger(maxTransshipments) || maxTransshipments < 0) {
      return HttpResponse.json({ message: '積み替えの上限は 0 以上にしてください' }, { status: 400 })
    }
    if (maxTransshipments > 3) {
      return HttpResponse.json({ message: '積み替えの上限は 3 回までにしてください' }, { status: 400 })
    }

    // 期限は日付。業務タイムゾーンのその日の終わりまでに着けばよい（ADR-017 決定 3）
    const deadlineInstant = businessDateEndInstant(deadline)
    // 出発希望日は、業務タイムゾーンでのその日の始まりが境目（US10）
    const earliestDeparture = params.get('earliestDeparture')
    const earliestDepartureInstant =
      earliestDeparture === null ? null : businessDateStartInstant(earliestDeparture)
    const now = new Date().toISOString()
    const usable = voyages.filter(
      (voyage) => voyage.supportedCargoTypes.includes(cargoType) && voyage.departureTime >= now,
    )

    // **誤配のあとの組み直しでは期限で弾かない**（US28-4）。本物の routingms と同じ
    const reroute = params.get('reroute') === 'true'
    const candidates = findMockRoutes(
      usable,
      origin,
      destination,
      deadlineInstant,
      maxTransshipments,
      earliestDepartureInstant,
      !reroute,
    ).sort((a, b) => {
      const direct = Number(b.length === 1) - Number(a.length === 1)
      if (direct !== 0) return direct
      const arrival = a[a.length - 1].arrivalTime.localeCompare(b[b.length - 1].arrivalTime)
      if (arrival !== 0) return arrival
      return a.length - b.length
    })

    return HttpResponse.json({
      candidates: candidates.map((legs, index) => toMockCandidate(legs, index + 1)),
      totalCount: candidates.length,
      appliedCriteria: {
        originUnLocode: origin,
        originName: originLocation.name,
        destinationUnLocode: destination,
        destinationName: destinationLocation.name,
        arrivalDeadline: deadlineInstant,
        cargoType,
        maxTransshipments,
        earliestDeparture: earliestDepartureInstant,
      },
    })
  }),

  http.get(`${API_PATHS.voyages}/:voyageNumber`, ({ params }) => {
    const found = voyages.find((voyage) => voyage.voyageNumber === params.voyageNumber)
    return found === undefined
      ? HttpResponse.json({ message: '指定された航海が見つかりません' }, { status: 404 })
      : HttpResponse.json(found)
  }),

  http.post(API_PATHS.voyages, async ({ request }) => {
    const body = (await request.json()) as MockVoyageRequest
    const existing = voyages.find((voyage) => voyage.voyageNumber === body.voyageNumber)
    if (existing !== undefined) {
      // 重複は失敗ではなく、上書きするかを選ばせるための応答である
      const incoming = toMockVoyage(body)
      const changes = differenceOf(existing, incoming)
      return HttpResponse.json(
        {
          message:
            changes.length === 0
              ? '同じ航海番号のスケジュールが既に登録されています。変更はありません'
              : '同じ航海番号のスケジュールが既に登録されています',
          hasChanges: changes.length > 0,
          existing,
          changes,
        },
        { status: 409 },
      )
    }
    const created = toMockVoyage(body)
    voyages.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put(`${API_PATHS.voyages}/:voyageNumber`, async ({ request, params }) => {
    const body = (await request.json()) as MockVoyageRequest
    const index = voyages.findIndex((voyage) => voyage.voyageNumber === params.voyageNumber)
    if (index < 0) {
      return HttpResponse.json({ message: '指定された航海が見つかりません' }, { status: 404 })
    }
    const updated = toMockVoyage(body)
    voyages[index] = updated
    return HttpResponse.json(updated)
  }),
]
