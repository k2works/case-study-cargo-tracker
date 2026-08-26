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

/**
 * 円に丸める（[ADR-027] 決定 2）。
 *
 * <p><strong>サーバと同じ向きに丸める。</strong>サーバは `HALF_UP`——0 から遠いほうへ
 * 丸めるため -1.5 は -2 になる。JavaScript の `Math.round` は +∞ 方向に丸めるので
 * -1.5 が -1 になり、<strong>小計が負になる調整</strong>（大幅な減額・補償）で
 * プレビューと確定後の合計が 1 円ずれる。
 *
 * <p>そもそも画面で金額を計算しないのが筋だが、調整を入れた結果をその場で見せるには
 * 画面で足すしかない（算出中はサーバに保存しない——決定 3）。<strong>ならばせめて
 * 同じ向きに丸める。</strong>
 */
export function roundYen(value: number): number {
  return Math.sign(value) * Math.round(Math.abs(value))
}
