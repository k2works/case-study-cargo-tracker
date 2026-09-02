import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { API_PATHS } from "../../config/api";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";
import { TrackingExceptionsPage } from "../tracking-exceptions-page";

/**
 * 未解決の例外の一覧（横断規約・US28-7）。
 *
 * **件数の遷移先である。** 「3 件ある」と分かっても、どの貨物かが分からなければ
 * 次にすることが無い。
 *
 * **一覧を行き止まりにしない。** 誤配は**経路設計者が直す**——気づく人（追跡管理者）と
 * 直す人が違うため、予約へ渡す導線が要る（[ADR-026] 決定 6）。
 */
const TRACKING_NUMBER = "TRK-20260823-0003";
const BOOKING_ID = "BKG-2026000006";

function exceptionOf(exceptionType: string, label: string) {
  return {
    trackingNumber: TRACKING_NUMBER,
    bookingId: BOOKING_ID,
    status: "EXCEPTION",
    statusLabel: "例外発生",
    locationName: "Singapore",
    estimatedArrival: "2027-09-25",
    activeException: {
      id: 1,
      exceptionType,
      label,
      description: "予定ルート外の場所（SGSIN）で荷役が行われました",
      occurredAt: "2027-09-09 09:00",
      urgent: false,
    },
    events: [],
    exceptionHistory: [],
  };
}

function givenExceptions(exceptionType: string, label: string) {
  server.use(
    http.get(`${API_PATHS.trackingManagement}/exceptions`, () =>
      HttpResponse.json([exceptionOf(exceptionType, label)]),
    ),
  );
}

describe("未解決の例外の一覧", () => {
  beforeEach(() => {
    loginAs(["ROLE_TRACKER"]);
  });

  it("例外の種別・発生日時・状況が並ぶ", async () => {
    givenExceptions("MISROUTE", "誤配");
    renderWithProviders(<TrackingExceptionsPage />);

    expect(await screen.findByText(TRACKING_NUMBER)).toBeInTheDocument();
    expect(screen.getByText("誤配")).toBeInTheDocument();
    // いつから放置されているか。これが無いと、どれから手を付けるか決まらない
    expect(screen.getByText("2027-09-09 09:00")).toBeInTheDocument();
  });

  /**
   * **誤配は経路設計者が直す**（US28-7・[ADR-026] 決定 6）。
   *
   * 追跡番号から追跡の管理画面へ行けても、**経路は組み直せない**。
   * ここから予約へ辿れないと、「気づいたが何もできない」で終わる。
   */
  it("誤配には、予約を開く導線が出る", async () => {
    loginAs(["ROLE_ROUTING", "ROLE_TRACKER"]);
    givenExceptions("MISROUTE", "誤配");
    renderWithProviders(<TrackingExceptionsPage />);

    expect(
      await screen.findByRole("link", { name: /予約を開く/ }),
      "誤配に気づいても、組み直す画面へ行けない",
    ).toHaveAttribute("href", `/booking/${BOOKING_ID}`);
  });

  /**
   * **開けない画面へ誘導しない**（IT10 レビュー・user-representative 高 1）。
   *
   * <p>予約詳細は営業・経路設計者にしか開いていない（`App.tsx` のルートガード）。
   * この一覧を見るのは追跡管理者・荷役・営業であり、**誤配に最初に気づく追跡管理者が
   * 押すと /403 に飛ぶ**。押した先で断られる導線は、気づく手段を無くすより悪い
   * ——「自分の権限が足りない」のか「システムが壊れている」のか判別できない。
   */
  it("予約を開けないロールには、リンクの代わりに次に起きることを伝える", async () => {
    // 経理は予約詳細を開かない（App.tsx のルートガードと同じ範囲）
    loginAs(["ROLE_ACCOUNTANT"]);
    givenExceptions("MISROUTE", "誤配");
    renderWithProviders(<TrackingExceptionsPage />);

    await screen.findByText(TRACKING_NUMBER);
    expect(
      screen.queryByRole("link", { name: /予約を開く/ }),
      "押すと 403 になるリンクを出している",
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/経路設計者が組み直します/),
      "行き止まり。次に何が起きるか分からない",
    ).toBeInTheDocument();
  });

  /** 誤配以外では出さない。**遅延や破損は追跡管理者が追跡の画面で対応する**。 */
  it("誤配以外の例外には、予約を開く導線を出さない", async () => {
    givenExceptions("DELAY", "遅延");
    renderWithProviders(<TrackingExceptionsPage />);

    await screen.findByText(TRACKING_NUMBER);
    expect(screen.queryByRole("link", { name: /予約を開く/ })).not.toBeInTheDocument();
  });
});
