import { describe, expect, it } from "vitest";

import { baseAmountOf, basisOf, estimateBaseAmount, yen } from "../billing";
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

  /**
   * **地域係数はサーバと同じ値である**（[ADR-027] 決定 1 の改訂）。
   *
   * <p>デモ 8・デモ 9 はこのモックの上で走る。**モックだけが古いまま E2E が緑になる**
   * 形を塞ぐため、サーバ（`PortRegionTest` / `TransportChargeTest`）が固定している
   * 金額と同じ数字をここでも固定する（IT12 レビュー・tester 高 3）。
   */
  it("遠洋 1 区間の基本料金が、サーバと同じ金額になる", () => {
    // 50,000 × 6.0（遠洋）× 1.0（1,000kg）× 1.0 = 300,000
    // （`TransportChargeTest#同じ 1 区間でも、国内と遠洋で金額が違う` と同じ根拠）
    expect(
      estimateBaseAmount([{ loadRegion: "OCEAN", unloadRegion: "OCEAN" }], 1000, "GENERAL"),
    ).toBe(300_000);
    // 国内は変わらない——地域区分の追加は値上げではない
    expect(
      estimateBaseAmount(
        [{ loadRegion: "DOMESTIC", unloadRegion: "DOMESTIC" }],
        1000,
        "GENERAL",
      ),
    ).toBe(50_000);
  });

  /** **両端が違えば重いほうを採る**（向きに依らない）。 */
  it("両端の区分が違う区間は、重いほうの係数を採る", () => {
    const outbound = estimateBaseAmount(
      [{ loadRegion: "DOMESTIC", unloadRegion: "OCEAN" }],
      1000,
      "GENERAL",
    );
    const inbound = estimateBaseAmount(
      [{ loadRegion: "OCEAN", unloadRegion: "DOMESTIC" }],
      1000,
      "GENERAL",
    );

    expect(outbound).toBe(300_000);
    expect(inbound, "向きで金額が変わっている").toBe(outbound);
  });

  /**
   * **知らない値は断る**（本物の `PortRegion.of` / `CargoType.of` と同じ）。
   *
   * <p>既定値に倒すと、値を足したときに**その区分・その貨物だけ安く**なる。
   */
  it("扱いを決めていない地域区分・貨物種別は断る", () => {
    expect(() =>
      estimateBaseAmount([{ loadRegion: "MOON", unloadRegion: "OCEAN" }], 1000, "GENERAL"),
    ).toThrow();
    expect(() =>
      estimateBaseAmount(
        [{ loadRegion: "OCEAN", unloadRegion: "OCEAN" }],
        1000,
        "SOMETHING_NEW",
      ),
    ).toThrow();
  });

  it("負の金額を 0 から遠いほうへ丸める（Money の HALF_UP と同じ）", () => {
    // 調整で小計が負になると効く。Math.round は +∞ 方向に丸めるため -3 にならない
    expect(yen(-2.5).value).toBe(-3);
    expect(yen(2.5).value).toBe(3);
  });
});
