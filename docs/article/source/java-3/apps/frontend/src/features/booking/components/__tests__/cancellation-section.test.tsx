import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { bookings } from "../../../../mocks/data";
import { bookingHandlers } from "../../../../mocks/handlers/booking";
import {
  cancellationHandlers,
  cancellations,
  resetCancellations,
} from "../../../../mocks/handlers/cancellation";
import { BookingDetailPage } from "../../../../pages/booking-detail-page";
import { server } from "../../../../test/msw/server";
import {
  createTestQueryClient,
  loginAs,
  renderWithProviders,
} from "../../../../test/render";

/**
 * 予約詳細からのキャンセル申請（US30-1・US30-2・US30-3）。
 *
 * **営業担当者の手番。** 荷主から「止めてほしい」と言われるのは営業である。
 *
 * **出し分けはサーバが返す「行える操作」に従う。** 画面が状態名を見比べると、
 * 遷移の規則が集約・画面・モックの 3 か所に分かれる。
 */
const BOOKING_ID = "BKG-2026000001";

function bookingAt(status: string) {
  const booking = bookings.find((candidate) => candidate.bookingId === BOOKING_ID);
  if (booking === undefined) {
    throw new Error("前提の予約がモックに無い");
  }
  booking.bookingStatus = status;
  booking.lastHandlingLocationUnLocode = status === "IN_TRANSIT" ? "SGSIN" : null;
  return booking;
}

describe("予約詳細からのキャンセル申請（US30）", () => {
  beforeEach(() => {
    resetCancellations();
    loginAs(["ROLE_SALES"]);
    server.use(...bookingHandlers, ...cancellationHandlers);
  });

  function renderPage() {
    renderWithProviders(
      <BookingDetailPage />,
      [`/booking/${BOOKING_ID}`],
      createTestQueryClient(),
      { path: "/booking/:bookingId" },
    );
  }

  async function request(user: ReturnType<typeof userEvent.setup>) {
    await user.click(
      await screen.findByRole("button", { name: "キャンセルを申請する" }),
    );
    await user.type(screen.getByLabelText("キャンセルの理由"), "荷主都合");
    await user.click(screen.getByRole("button", { name: "申請する" }));
  }

  /** US30-2。**輸送開始前は即時に確定する。** 承認を待つ理由が無い。 */
  it("輸送開始前の申請は、その場でキャンセルが確定する", async () => {
    const booking = bookingAt("CONFIRMED");
    const user = userEvent.setup();
    renderPage();

    await request(user);

    expect(await screen.findByText(/キャンセルが確定しました/)).toBeInTheDocument();
    expect(booking.bookingStatus).toBe("CANCELLED");
  });

  /** US30-3。**輸送中は承認を待つ。** 貨物が船の上にあり、降ろす場所を決める必要がある。 */
  it("輸送中の申請は、承認待ちになる", async () => {
    const booking = bookingAt("IN_TRANSIT");
    const user = userEvent.setup();
    renderPage();

    await request(user);

    expect(await screen.findByText(/承認をお待ちください/)).toBeInTheDocument();
    expect(booking.bookingStatus).toBe("IN_TRANSIT");
    expect(cancellations[0].status).toBe("REQUESTED");
  });

  /** 理由は必須。あとから「なぜ止めたのか」を読むのは、荷主に説明する担当者である。 */
  it("理由を入れずには申請できない", async () => {
    bookingAt("CONFIRMED");
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "キャンセルを申請する" }),
    );

    expect(screen.getByLabelText("キャンセルの理由")).toBeRequired();
  });

  /**
   * **押せない操作を見せない。**
   *
   * すでにキャンセルされた予約に申請の枠を出すと、押した先で 409 になる。
   * 出し分けはサーバが返す「行える操作」に従う。
   */
  it("キャンセル済みの予約には、申請の枠が出ない", async () => {
    bookingAt("CANCELLED");
    renderPage();

    // 見出しは「予約 BKG-...」であり、番号だけの要素は無い
    await screen.findByRole("heading", { name: `予約 ${BOOKING_ID}` });
    expect(
      screen.queryByRole("button", { name: "キャンセルを申請する" }),
    ).not.toBeInTheDocument();
  });

  /** 申請の履歴が読める。承認待ちか却下かが分からないと、次の行動が決まらない。 */
  it("申請したあとは、いまどうなっているかが読める", async () => {
    bookingAt("IN_TRANSIT");
    const user = userEvent.setup();
    renderPage();

    await request(user);
    await screen.findByText(/承認をお待ちください/);

    expect(await screen.findByText("承認待ち")).toBeInTheDocument();
    expect(screen.getByText("荷主都合")).toBeInTheDocument();
  });

  /**
   * US30-10。**却下されて再申請しても、前回の却下理由が残る。**
   *
   * 最新の 1 件しか出さないと、**「なぜ一度断られたか」が予約詳細から消える**。
   * それは、次に荷主と話す営業がいちばん必要とする情報である。
   */
  it("却下されて再申請しても、前回の却下理由が残る", async () => {
    bookingAt("IN_TRANSIT");
    // 1 回目は却下された
    cancellations.push({
      cancellationId: 1,
      bookingId: BOOKING_ID,
      reason: "荷主都合",
      status: "REJECTED",
      requestedBy: "sales01",
      requestedAt: "2026-09-05T00:00:00Z",
      bookingStatusAtRequest: "IN_TRANSIT",
      dischargeLocationUnLocode: null,
      decidedBy: "tracker01",
      decidedAt: "2026-09-05T03:00:00Z",
      decisionReason: "積み替え済みのため",
    });
    const user = userEvent.setup();
    renderPage();

    // 2 回目を申請する
    await request(user);
    await screen.findByText(/承認をお待ちください/);

    // いまの申請は「承認待ち」
    expect(await screen.findByText("承認待ち")).toBeInTheDocument();
    // **前回の却下理由と決定者が残っている**
    expect(
      await screen.findByText("積み替え済みのため"),
      "前回の却下理由が消えている。なぜ一度断られたかが分からない",
    ).toBeInTheDocument();
    expect(screen.getByText(/tracker01/)).toBeInTheDocument();
  });
});
