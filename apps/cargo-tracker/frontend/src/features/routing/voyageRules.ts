/**
 * 航海に対して何ができるかの述語（domain-model.md「Voyage 集約の不変条件」）。
 *
 * <p>画面のボタン出し分けは投影の値を読むが、<b>判定は書き直さずここを呼ぶ</b>。
 * 予約側（transitions.ts）と同じ形にしている。写しを持つこと自体は避けられないが、
 * 写しが黙ってずれることは避けられる。</p>
 *
 * <p>Java 側の `Voyage.updateSchedule` と同じ条件であることは
 * `voyageRules.canon.test.ts` が集約のソースを読んで突き合わせる。</p>
 */
export interface VoyageState {
  readonly cancelled: boolean;
}

/** スケジュールを更新できるか（不変条件 5）。 */
export function canUpdateSchedule(voyage: VoyageState): boolean {
  return !voyage.cancelled;
}
