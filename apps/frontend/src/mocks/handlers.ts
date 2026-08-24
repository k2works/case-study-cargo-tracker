/**
 * MSW のハンドラ。
 *
 * <p><strong>バウンデッドコンテキストごとに分ける。</strong>1 ファイルに積み上げると、
 * 触るたびに関係のない規則まで読み直すことになり、本物と読み比べる作業（IT5 の Try 4）が
 * 実際にはやりにくくなる。1,381 行まで育ったところで分割した。
 *
 * <p>状態（予約・荷主・航海）は {@link ./data} が 1 か所で持つ。ファイルごとに配列を持つと、
 * 予約を作ったのに一覧に出ない、といった食い違いが起きる。
 */
import { adminHandlers } from './handlers/admin'
import { authHandlers } from './handlers/auth'
import { bookingHandlers } from './handlers/booking'
import { routingHandlers } from './handlers/routing'
import { shipperHandlers } from './handlers/shipper'
import { cancellationHandlers } from './handlers/cancellation'
import { customsHandlers } from './handlers/customs'
import { handlingHandlers } from './handlers/handling'
import { trackingHandlers } from './handlers/tracking'

export const handlers = [
  ...authHandlers,
  ...shipperHandlers,
  ...bookingHandlers,
  ...adminHandlers,
  ...routingHandlers,
  ...handlingHandlers,
  ...customsHandlers,
  ...cancellationHandlers,
  ...trackingHandlers,
]
