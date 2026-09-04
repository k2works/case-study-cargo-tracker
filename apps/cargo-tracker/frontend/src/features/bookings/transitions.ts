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

/** 経路設計へ引き渡せるか（US06）。集約と同じ述語を通す。 */
export function canRequestRouting(status: string): boolean {
  return canTransitionTo(status, 'ROUTE_PROPOSED');
}
