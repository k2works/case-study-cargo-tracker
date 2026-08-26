import { describe, expect, it } from "vitest";

import { baseAmountOf, basisOf, yen } from "../billing";
import type { MockBooking } from "../../data";

/**
 * **モックを本物より甘くしない。**
 *
 * `billing.ts` の冒頭は「本物と同じ式で計算する」と宣言しているが、宣言だけでは
 * 守られない。ここで**サーバ（`TransportCharge` / `Money`）が返す値そのもの**を
 * 固定し、モックがずれたら赤にする。
 */
describe("精算のモックが本物と同じ答えを返す", () => {
  function bookingWith(weightKg: number, legCount: number): MockBooking {
    return {
      weightKg,
      type: "GENERAL",
      // **国内の区間で作る。**地域区分を入れる前と同じ係数（1.0）になるため、
      // ここで見たいこと（丸めの向きと精度）だけが効く
      itinerary: Array.from({ length: legCount }, () => ({
        loadUnLocode: 'JPTYO',
        unloadUnLocode: 'JPYOK',
      })) as never,
    } as unknown as MockBooking;
  }

  it("重量係数を小数第 4 位で丸める（TransportCharge.weightFactor と同じ）", () => {
    // 1234.5678 / 1000 = 1.2345678。サーバは scale 4 の HALF_UP で 1.2346 にする
    expect(basisOf(bookingWith(1234.5678, 1)).weightFactor).toBe(1.2346);
  });

  it("重量係数を丸めた結果が基本料金に効く", () => {
    // 50,000 × 1 区間 × 1.2346 × 1.0 = 61,730（丸める前の 1.2345678 なら 61,728）
    expect(baseAmountOf(basisOf(bookingWith(1234.5678, 1))).value).toBe(61_730);
  });

  it("負の金額を 0 から遠いほうへ丸める（Money の HALF_UP と同じ）", () => {
    // 調整で小計が負になると効く。Math.round は +∞ 方向に丸めるため -3 にならない
    expect(yen(-2.5).value).toBe(-3);
    expect(yen(2.5).value).toBe(3);
  });
});
