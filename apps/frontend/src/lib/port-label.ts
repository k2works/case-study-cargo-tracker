/**
 * 港を画面に出す形に整える。
 *
 * この画面群は港を「名前（符号）」の形で出している（出発地・目的地・旅程の各区間）。
 * **整形を 1 か所に置く。** 呼ぶ場所ごとに組み立てると、誤配のバナーだけ符号のまま、
 * といった食い違いが起きる（IT10 レビュー低 15 で実際に起きた）。
 *
 * 名前が引けないときは符号だけを返す。**記録そのものは消さない**——誤配は
 * 「予定していない港に降ろされた」事実であり、その港が地点マスタに載っている
 * 保証はない。名前が無いことを理由に消すと、最も異常な誤配ほど画面から消える。
 *
 * @param unLocode UN/LOCODE。無ければ null
 * @param name 港の名前。引けなければ null
 * @param fallback 符号も名前も無いときに出す文字列
 */
export function portLabel(
  unLocode: string | null | undefined,
  name: string | null | undefined,
  fallback: string,
): string {
  if (!unLocode) {
    return fallback
  }
  return name ? `${name}（${unLocode}）` : unLocode
}
