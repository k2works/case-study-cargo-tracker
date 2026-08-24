import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  cancellationHandlers,
  cancellations,
  resetCancellations,
} from "../../mocks/handlers/cancellation";
import { bookings } from "../../mocks/data";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";
import { CancellationsPage } from "../cancellations-page";

/**
 * 輸送中のキャンセル承認（US30-4・US30-5・US30-7）。
 *
 * **追跡管理者が使う。** 貨物が船の上にあるため、どこで降ろすかを決めないと
 * キャンセルできない。
 */
const BOOKING_ID = "BKG-2026000001";

function inTransitBookingWithRequest() {
  const booking = bookings.find((candidate) => candidate.bookingId === BOOKING_ID);
  if (booking === undefined) {
    throw new Error("前提の予約がモックに無い");
  }
  booking.bookingStatus = "IN_TRANSIT";
  booking.lastHandlingLocationUnLocode = "SGSIN";
  booking.itinerary = [
    {
      voyageNumber: "V0100",
      loadUnLocode: "JPTYO",
      loadName: "Tokyo",
      unloadUnLocode: "SGSIN",
      unloadName: "Singapore",
      loadTime: "2027-09-01T09:00:00.000Z",
      unloadTime: "2027-09-05T09:00:00.000Z",
    },
    {
      voyageNumber: "V0200",
      loadUnLocode: "SGSIN",
      loadName: "Singapore",
      unloadUnLocode: "USLAX",
      unloadName: "Los Angeles",
      loadTime: "2027-09-06T09:00:00.000Z",
      unloadTime: "2027-09-15T09:00:00.000Z",
    },
  ];
  cancellations.push({
    cancellationId: 1,
    bookingId: BOOKING_ID,
    reason: "荷主都合",
    status: "REQUESTED",
    requestedBy: "sales01",
    requestedAt: "2027-09-02T10:00:00.000Z",
    bookingStatusAtRequest: "IN_TRANSIT",
    dischargeLocationUnLocode: null,
    decidedBy: null,
    decidedAt: null,
    decisionReason: null,
  });
  return booking;
}

describe("キャンセル承認（US30）", () => {
  beforeEach(() => {
    resetCancellations();
    loginAs(["ROLE_TRACKER"]);
    server.use(...cancellationHandlers);
  });

  function renderPage() {
    renderWithProviders(<CancellationsPage />);
  }

  it("承認待ちの申請が、判断に要るものと一緒に並ぶ", async () => {
    inTransitBookingWithRequest();
    renderPage();

    const row = within(
      (await screen.findByText(BOOKING_ID)).closest("tr") as HTMLElement,
    );
    expect(row.getByText("荷主都合")).toBeInTheDocument();
    expect(row.getByText("sales01")).toBeInTheDocument();
    // 申請時点の予約状態は**キャンセル料の根拠**になる（US23・IT11）
    expect(row.getByText("輸送中")).toBeInTheDocument();
  });

  /**
   * **[ADR-025] 決定 4。全港から選ばせない。**
   *
   * 船が寄らない港を指定できると、荷降しできない約束を荷主にすることになる。
   * 候補は「現在地の港」と「次の寄港地」だけである。
   */
  it("陸揚げ地の候補は、現在地の港と次の寄港地だけ", async () => {
    inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    const options = within(
      await screen.findByLabelText("陸揚げ地"),
    ).getAllByRole("option");
    const values = options.map((option) => option.getAttribute("value"));

    expect(values).toContain("SGSIN");
    expect(values).toContain("USLAX");
    // 旅程に無い港は候補に出さない
    expect(values).not.toContain("USNYC");
    expect(values).not.toContain("JPYOK");
  });

  /** なぜ候補なのかを出す。港の名前だけでは、どれを選べばよいか決められない。 */
  it("候補には、なぜ候補なのかが添えられる", async () => {
    inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    const select = await screen.findByLabelText("陸揚げ地");
    expect(within(select).getByText(/Singapore.*現在地の港/)).toBeInTheDocument();
    expect(within(select).getByText(/Los Angeles.*次の寄港地/)).toBeInTheDocument();
  });

  /** US30-5。**陸揚げ地なしでは承認できない**。 */
  it("陸揚げ地を選ばずには承認できない", async () => {
    inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    expect(await screen.findByLabelText("陸揚げ地")).toBeRequired();
  });

  it("承認すると、キャンセルが確定して一覧から消える", async () => {
    const booking = inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    await user.selectOptions(await screen.findByLabelText("陸揚げ地"), "SGSIN");
    await user.type(screen.getByLabelText("決定の理由"), "荷主と合意");
    await user.click(screen.getByRole("button", { name: "承認する" }));

    expect(await screen.findByText(/承認しました/)).toBeInTheDocument();
    expect(booking.bookingStatus).toBe("CANCELLED");
    expect(cancellations[0].dischargeLocationUnLocode).toBe("SGSIN");
  });

  /**
   * US30-7。**却下しても予約は輸送中のまま。**
   *
   * 却下は「キャンセルしない」という決定である。予約まで止まると、貨物は
   * 行き先を失ったまま船に乗り続ける。
   */
  it("却下しても、予約は輸送中のまま維持される", async () => {
    const booking = inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    await user.type(screen.getByLabelText("決定の理由"), "積み替え済みのため");
    await user.click(screen.getByRole("button", { name: "却下する" }));

    expect(await screen.findByText(/却下しました/)).toBeInTheDocument();
    expect(booking.bookingStatus).toBe("IN_TRANSIT");
    expect(cancellations[0].status).toBe("REJECTED");
  });

  /** 却下の理由は申請者と荷主に伝わる。空では送れない。 */
  it("理由を入れずには却下できない", async () => {
    inTransitBookingWithRequest();
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "開く" }));

    await user.click(screen.getByRole("button", { name: "却下する" }));

    expect(await screen.findByText(/却下の理由を入力してください/)).toBeInTheDocument();
    expect(cancellations[0].status).toBe("REQUESTED");
  });

  it("承認待ちが無ければ、その旨を出す", async () => {
    renderPage();

    expect(
      await screen.findByText("承認待ちのキャンセル申請はありません。"),
    ).toBeInTheDocument();
  });
});
