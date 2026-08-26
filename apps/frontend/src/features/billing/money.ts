import type { Money } from './types'

/**
 * 金額を画面に出す形に整える。
 *
 * <p><strong>ここでは計算しない</strong>（[ADR-027] 決定 2）。丸めはサーバが済ませており、
 * 画面はサーバが返した値をそのまま出す。フロントで丸めると、丸める場所が 2 か所になり、
 * <strong>画面と保存値が食い違う</strong>。
 *
 * <p>整形を 1 か所に置くのは、呼ぶ場所ごとに組み立てると桁区切りの有無が画面ごとに
 * 変わるためである（誤配の港名で同じ食い違いが起きた——IT10 レビュー低 15）。
 */
export function formatYen(money: Money | null | undefined): string {
  if (money === null || money === undefined) {
    return '—'
  }
  const sign = money.value < 0 ? '-' : ''
  return `${sign}¥${Math.abs(money.value).toLocaleString('ja-JP')}`
}

/** 割引率などの率を百分率で出す。**率そのものを出す**——額だけでは率を復元できない。 */
export function formatRate(rate: number | null | undefined): string {
  if (rate === null || rate === undefined) {
    return '—'
  }
  return `${(rate * 100).toFixed(1).replace(/\.0$/, '')}%`
}
