/**
 * 予約の状態遷移表（domain-model.md「BookingStatus 状態遷移（正典）」）。
 *
 * <p>画面のボタンは投影の booking_status を読むが、<b>判定は書き直さずここを呼ぶ</b>。
 * 画面ごとに if を書くと、遷移が変わったときに直し漏れた画面だけが押せる／押せない
 * ままになる。</p>
 *
 * <p>バックエンドの BookingStatus と同じ内容であることは
 * transitions.canon.test.ts が Java の正典を読んで突き合わせる。ブラウザから集約は
 * 呼べないので、写しを持つこと自体は避けられない。避けられるのは、写しが黙って
 * ずれることのほう。</p>
 */
export const BOOKING_TRANSITIONS: Readonly<Record<string, readonly string[]>> = {
  PRELIMINARY: ['ROUTE_PROPOSED', 'CANCELLED'],
  ROUTE_PROPOSED: ['ROUTE_PROPOSED', 'ROUTE_NOTIFIED', 'CANCELLED'],
  ROUTE_NOTIFIED: ['ROUTE_NOTIFIED', 'ROUTE_PROPOSED', 'CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['TRACKING_ISSUED', 'CANCELLED'],
  TRACKING_ISSUED: ['IN_TRANSIT', 'CANCELLED'],
  IN_TRANSIT: ['IN_TRANSIT', 'DELIVERED', 'CANCELLED'],
  DELIVERED: ['SETTLED'],
  SETTLED: [],
  CANCELLED: [],
};

export function canTransitionTo(status: string, next: string): boolean {
  return (BOOKING_TRANSITIONS[status] ?? []).includes(next);
}

/**
 * 入力の誤りを直せるか（US32）。集約と同じ述語を通す。
 *
 * <p>遷移ではないので BOOKING_TRANSITIONS では表せない。Java 側の
 * BookingStatus.canUpdateSpecification と同じ判断であることは
 * transitions.canon.test.ts が正典を読んで突き合わせる。</p>
 */
export function canUpdateSpecification(status: string): boolean {
  return status === 'PRELIMINARY';
}

/**
 * 経路設計へ引き渡せるか（US06）。集約と同じ述語を通す。
 *
 * <p><b>遷移先では表せない。</b> ROUTE_PROPOSED への自己遷移は経路の確定と条件の
 * 調整のもので、引き渡しではない。`canTransitionTo(status, 'ROUTE_PROPOSED')` で
 * 代用すると、引き渡し済みの予約に「経路設計を依頼する」が出て、押すと確定済みの
 * 経路が理由も残さず未設計に戻る（マニュアルのキャプチャで実測）。</p>
 *
 * <p>Java 側の BookingStatus.canRequestRouting と同じ判断であることは
 * transitions.canon.test.ts が正典を読んで突き合わせる。</p>
 */
export function canRequestRouting(status: string): boolean {
  return status === 'PRELIMINARY';
}

/**
 * 経路を確定できるか（US09）。
 *
 * <p>集約（{@code Cargo.assignRoute}）は `ROUTING_REQUESTED` と `MISROUTED` だけを
 * 受ける。画面が持たないと、確定できない予約でボタンが出て、押してから断られる
 * （IT5 レビュー 中 5・7）。</p>
 *
 * <p>これは <b>routingStatus</b> の判断で、予約の状態（bookingStatus）とは別の軸。</p>
 */
export function canAssignRoute(routingStatus: string): boolean {
  return routingStatus === 'ROUTING_REQUESTED' || routingStatus === 'MISROUTED';
}
