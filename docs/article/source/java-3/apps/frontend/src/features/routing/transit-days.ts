/**
 * 所要日数を出す。
 *
 * <p>出発から到着までを<strong>日単位で切り捨てる</strong>。バックエンドの
 * `TransitPath#transitDays`（`ChronoUnit.DAYS.between`）と同じ規則である。
 *
 * <p>ここに 1 つだけ置くのは、同じ数を別々に計算していたためである。予約詳細は
 * 四捨五入して下限を 1 日にしており、経路設計の画面（サーバが返す値）と 1 日ずれていた。
 * 営業が荷主に伝える日数と、経路設計者が見ている日数が違うと、どちらが正しいのかを
 * 確かめる手段が現場に無い。
 *
 * <p>下限 1 日は入れない。日をまたがない輸送は 0 日であり、サーバもそう答える。
 * 画面だけが 1 日と言うと、そこでまたずれる。
 */
export function transitDaysBetween(departureTime: string, arrivalTime: string): number {
  const millis = new Date(arrivalTime).getTime() - new Date(departureTime).getTime()
  return Math.floor(millis / (24 * 60 * 60 * 1000))
}
