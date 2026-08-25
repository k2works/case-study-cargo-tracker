import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { bookings } from "../../mocks/data";
import { bookingHandlers } from "../../mocks/handlers/booking";
import {
  cancellationHandlers,
  cancellations,
  resetCancellations,
} from "../../mocks/handlers/cancellation";
import { AwaitingDischargePage } from "../awaiting-discharge-page";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";

/**
 * 陸揚げ待ち（IT10 返済枠 0.3）。
 *
 * **荷役の担当者には、陸揚げ地が決まったことを知る入口が無かった。**
 * 作業指示は自動で作られず（[ADR-025] 決定 5）、承認した追跡管理者からの連絡が
 * 唯一の担保だった——**連絡を忘れると、貨物は指定した港を通り過ぎる**。
 */
const BOOKING_ID = "BKG-2026000005";

function approvedCancellation(dischargeLocationUnLocode: string | null) {
  cancellations.push({
    cancellationId: 1,
    bookingId: BOOKING_ID,
    reason: "荷主都合による中止",
    status: "APPROVED",
    requestedBy: "sales01",
    requestedAt: "2026-09-05T00:00:00Z",
    bookingStatusAtRequest: "IN_TRANSIT",
    dischargeLocationUnLocode,
    decidedBy: "tracker01",
    decidedAt: "2026-09-05T03:00:00Z",
    decisionReason: "現在地の港で陸揚げする",
  });
}

describe("陸揚げ待ち（IT10 返済枠 0.3）", () => {
  beforeEach(() => {
    resetCancellations();
    loginAs(["ROLE_HANDLER"]);
    server.use(...bookingHandlers, ...cancellationHandlers);
    const booking = bookings.find((candidate) => candidate.bookingId === BOOKING_ID);
    if (booking !== undefined) booking.bookingStatus = "IN_TRANSIT";
  });

  it("承認済みで陸揚げ地が決まった貨物が並ぶ", async () => {
    approvedCancellation("SGSIN");
    renderWithProviders(<AwaitingDischargePage />);

    expect(await screen.findByText(BOOKING_ID)).toBeInTheDocument();
    // **どこで降ろすか**が分からないと手配できない
    expect(screen.getByText("Singapore")).toBeInTheDocument();
    expect(screen.getByText(/tracker01/)).toBeInTheDocument();
  });

  /** 一覧を行き止まりにしない。貨物の中身は予約詳細で見る。 */
  it("予約 ID から予約詳細へ行ける", async () => {
    approvedCancellation("SGSIN");
    renderWithProviders(<AwaitingDischargePage />);

    expect(await screen.findByRole("link", { name: BOOKING_ID })).toHaveAttribute(
      "href",
      `/booking/${BOOKING_ID}`,
    );
  });

  /**
   * **陸揚げ地が決まっていない申請は出さない。**
   *
   * 降ろす場所が決まっていない貨物を一覧に出すと、荷役の担当者は何も手配できない
   * ——「見たが何もできなかった」が積み上がると、一覧そのものが読まれなくなる。
   */
  it("陸揚げ地が決まっていない申請は出ない", async () => {
    approvedCancellation(null);
    renderWithProviders(<AwaitingDischargePage />);

    expect(
      await screen.findByText("陸揚げ待ちの貨物はありません。"),
    ).toBeInTheDocument();
  });

  it("承認待ちの申請は、まだ陸揚げ待ちではない", async () => {
    cancellations.push({
      cancellationId: 2,
      bookingId: BOOKING_ID,
      reason: "荷主都合",
      status: "REQUESTED",
      requestedBy: "sales01",
      requestedAt: "2026-09-05T00:00:00Z",
      bookingStatusAtRequest: "IN_TRANSIT",
      dischargeLocationUnLocode: null,
      decidedBy: null,
      decidedAt: null,
      decisionReason: null,
    });
    renderWithProviders(<AwaitingDischargePage />);

    expect(
      await screen.findByText("陸揚げ待ちの貨物はありません。"),
    ).toBeInTheDocument();
  });
});
