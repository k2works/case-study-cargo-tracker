import type { RouteCandidate } from './types'

/**
 * 経路候補の見せ方。
 *
 * <p><strong>1 か所に置く。</strong>候補の表・確定の確認・該当なしの案内はどれも同じ言葉で
 * 経路を示す必要がある。別々に書くと、同じ経路が画面の場所によって違う書き方になる。
 */

/** 費用は万円単位の概算で示す（[ADR-018]）。円単位で出すと請求額に見える。 */
export function formatCost(amount: number): string {
  return `約 ${Math.round(amount / 10000).toLocaleString('ja-JP')} 万円`
}

/** 待ち時間を「1 日 14 時間」の形で表す。分のままでは長さが直感的に分からない。 */
export function formatLayover(minutes: number): string {
  const days = Math.floor(minutes / (24 * 60))
  const hours = Math.floor((minutes % (24 * 60)) / 60)
  if (days === 0) {
    return `${hours} 時間`
  }
  return hours === 0 ? `${days} 日` : `${days} 日 ${hours} 時間`
}

/** 経路を「東京 →（上海）→ ロサンゼルス」の形で表す。 */
export function describeRoute(
  candidate: RouteCandidate,
  originName: string,
  destinationName: string,
): string {
  const via = candidate.transitPorts
    .map((port) =>
      port.layoverMinutes === null
        ? `（${port.name} / ${port.unLocode}）`
        : // どこで止まるかと、そこで何時間待つかは一続きの情報。分けて置くと読み合わせが要る
          `（${port.name} / ${port.unLocode}・待ち ${formatLayover(port.layoverMinutes)}）`,
    )
    .join(' → ')
  return via === ''
    ? `${originName} → ${destinationName}`
    : `${originName} → ${via} → ${destinationName}`
}
