/**
 * 経路候補の探索（[ADR-017]・[ADR-018] の写し）。
 *
 * <p><strong>本物と同じ規則で探す。</strong>ここが甘いと、画面は候補を出すのに実物では
 * 出ない（あるいはその逆）という食い違いが起きる。積み替えの最低時間・推奨順・費用の概算は
 * すべてサーバ側の実装と対になっている。
 */
import { type MockVoyage, LOCATIONS } from './data'
import { businessLocalToInstant, formatBusinessDate } from '../lib/business-time'

export const MOCK_MINIMUM_TRANSSHIPMENT_MS = 6 * 60 * 60 * 1000

export type MockLeg = {
  voyageNumber: string
  vesselName: string
  carrierName: string
  fromUnLocode: string
  fromName: string
  toUnLocode: string
  toName: string
  departureTime: string
  arrivalTime: string
}

/** その航海で from から乗って降りられる区間を、寄港の順序どおりに挙げる。 */
export function mockDeparturesFrom(voyage: MockVoyage, from: string, readyAt: string | null): MockLeg[] {
  const ports = [
    voyage.movements[0].departureUnLocode,
    ...voyage.movements.map((movement) => movement.arrivalUnLocode),
  ]
  const legs: MockLeg[] = []
  ports.forEach((port, loadOrder) => {
    if (port !== from || loadOrder >= voyage.movements.length) {
      return
    }
    const departure = voyage.movements[loadOrder].departureTime
    if (
      readyAt !== null &&
      new Date(departure).getTime() - new Date(readyAt).getTime() < MOCK_MINIMUM_TRANSSHIPMENT_MS
    ) {
      return
    }
    for (let unloadOrder = loadOrder + 1; unloadOrder <= voyage.movements.length; unloadOrder += 1) {
      const arrival = voyage.movements[unloadOrder - 1].arrivalTime
      const to = ports[unloadOrder]
      if (to === from) {
        continue
      }
      legs.push({
        voyageNumber: voyage.voyageNumber,
        vesselName: voyage.vesselName,
        carrierName: voyage.carrierName,
        fromUnLocode: from,
        fromName: LOCATIONS.find((location) => location.unLocode === from)?.name ?? from,
        toUnLocode: to,
        toName: LOCATIONS.find((location) => location.unLocode === to)?.name ?? to,
        departureTime: departure,
        arrivalTime: arrival,
      })
    }
  })
  return legs
}

/** 深さ優先で経路を挙げる。一度出た港へは戻らない（ADR-018）。 */
export function findMockRoutes(
  voyages: MockVoyage[],
  from: string,
  destination: string,
  deadline: string,
  maxTransshipments: number,
  /** 荷物が出せるようになる時刻。本物と同じく、これより前に出る便には積めない（US10） */
  earliestDeparture: string | null = null,
  readyAt: string | null = null,
  visited: string[] = [],
  arrivedOn: string | null = null,
): MockLeg[][] {
  if (visited.length > maxTransshipments) {
    return []
  }
  const found: MockLeg[][] = []
  for (const voyage of voyages) {
    // 同じ船に乗り直すのは積み替えではない（サーバの TransitPathFinder と同じ規則）
    if (voyage.voyageNumber === arrivedOn) {
      continue
    }
    for (const leg of mockDeparturesFrom(voyage, from, readyAt)) {
      if (leg.arrivalTime > deadline) {
        continue
      }
      if (earliestDeparture !== null && leg.departureTime < earliestDeparture) {
        continue
      }
      if (leg.toUnLocode === destination) {
        found.push([leg])
        continue
      }
      if (visited.includes(leg.toUnLocode) || leg.toUnLocode === from) {
        continue
      }
      const rest = findMockRoutes(
        voyages,
        leg.toUnLocode,
        destination,
        deadline,
        maxTransshipments,
        earliestDeparture,
        leg.arrivalTime,
        [...visited, from],
        leg.voyageNumber,
      )
      rest.forEach((tail) => found.push([leg, ...tail]))
    }
  }
  return found
}

/** 推奨順（直行優先 → 到着の早い順 → 積み替えの少ない順）と費用の概算（ADR-018）。 */
export function toMockCandidate(legs: MockLeg[], rank: number) {
  const departureTime = legs[0].departureTime
  const arrivalTime = legs[legs.length - 1].arrivalTime
  const transitDays = Math.floor(
    (new Date(arrivalTime).getTime() - new Date(departureTime).getTime()) / (24 * 60 * 60 * 1000),
  )
  // 積み替え港の待ち時間。本物と同じく、前の区間の到着から次の区間の出発まで
  const transitPorts = legs.slice(1).map((leg, index) => ({
    unLocode: leg.fromUnLocode,
    name: leg.fromName,
    layoverMinutes: Math.round(
      (new Date(leg.departureTime).getTime() - new Date(legs[index].arrivalTime).getTime()) / 60000,
    ),
  }))
  return {
    rank,
    direct: legs.length === 1,
    voyageNumbers: legs.map((leg) => leg.voyageNumber),
    departureTime,
    arrivalTime,
    transitDays,
    transshipmentCount: legs.length - 1,
    transitPorts,
    estimatedCost: 200000 * legs.length + 30000 * transitDays + 50000 * (transitPorts.length + 2),
    legs,
  }
}

/**
 * 動作確認用の航海を最初から置く（IT4 / US08）。
 *
 * 経路候補の画面は、探索の材料が無いと何も確かめられない。その場で登録する経路も
 * 通せるが、モックは画面を読み直すと消えるため、それだけだと「読み直したら候補が
 * 消えた」ときに画面の不具合と区別がつかない（引き渡し済みの予約を置いたのと同じ理由）。
 *
 * 直行 1 本と、シンガポールで積み替える 2 本。**直行のほうが遅く着く**ようにしてある。
 * 推奨順が「直行を最優先」であることを、順序が入れ替わる形で確かめられる。
 */
export function seedVoyage(
  voyageNumber: string,
  legs: [string, string, number, number][],
): MockVoyage {
  const movements = legs.map(([from, to, departureDays, arrivalDays]) => ({
    departureUnLocode: from,
    departureName: LOCATIONS.find((location) => location.unLocode === from)?.name ?? from,
    arrivalUnLocode: to,
    arrivalName: LOCATIONS.find((location) => location.unLocode === to)?.name ?? to,
    departureTime: daysFromNow(departureDays),
    arrivalTime: daysFromNow(arrivalDays),
  }))
  return {
    voyageNumber,
    vesselName: `${voyageNumber} 丸`,
    carrierName: 'デモ海運',
    supportedCargoTypes: ['GENERAL', 'REFRIGERATED'],
    originUnLocode: movements[0].departureUnLocode,
    originName: movements[0].departureName,
    destinationUnLocode: movements[movements.length - 1].arrivalUnLocode,
    destinationName: movements[movements.length - 1].arrivalName,
    departureTime: movements[0].departureTime,
    arrivalTime: movements[movements.length - 1].arrivalTime,
    movements,
  }
}

/** 今日から n 日後の 09:00（業務タイムゾーン）を UTC の ISO 8601 で返す。 */
export function daysFromNow(days: number): string {
  const at = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
  return businessLocalToInstant(`${formatBusinessDate(at)}T09:00`)
}

export const voyages: MockVoyage[] = [
  // 直行。遅く着くが積み替えが無い。**途中で横浜に寄る**ので、航海詳細で
  // 寄港地と区間ごとの時刻を確かめられる（1 区間の便だと詳細画面の意味が伝わらない）
  seedVoyage('DEMO-DIRECT', [
    ['JPTYO', 'JPYOK', 10, 11],
    ['JPYOK', 'USLAX', 12, 28],
  ]),
  // 積み替え。早く着くが 1 回積み替える
  seedVoyage('DEMO-LEG1', [['JPTYO', 'SGSIN', 11, 14]]),
  seedVoyage('DEMO-LEG2', [['SGSIN', 'USLAX', 15, 26]]),
]
