import type { LocationOption } from './types'

/**
 * 地点を「名前（コード）」で出す。
 *
 * **画面ごとに書き方を変えない。** 予約や精算の画面は名前で出しており、見積だけが
 * UN/LOCODE のままだと、営業担当者は同じ港を 2 つの呼び方で覚えることになる
 * （誤配の港名で同じ食い違いが起きた——IT10 レビュー低 15）。
 *
 * **知らないコードはそのまま出す。** 「—」にすると、地点マスタに無い港なのか
 * 読み込み前なのかが読み分けられない。
 */
export function locationName(locations: LocationOption[], unLocode: string): string {
  const found = locations.find((location) => location.unLocode === unLocode)
  return found === undefined ? unLocode : `${found.name}（${found.unLocode}）`
}
