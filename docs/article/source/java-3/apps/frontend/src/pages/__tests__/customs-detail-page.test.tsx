import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  customsDeclarations,
  customsHandlers,
  resetCustomsDeclarations,
} from "../../mocks/handlers/customs";
import { server } from "../../test/msw/server";
import {
  createTestQueryClient,
  loginAs,
  renderWithProviders,
} from "../../test/render";
import { CustomsDetailPage } from "../customs-detail-page";

/**
 * 通関申告の詳細と状態の更新（US29-2・US29-8）。
 *
 * **状態を更新できるのは追跡管理者だけ**（[ADR-025] 決定 6）。荷役作業員は読むだけで、
 * **押せない操作を見せない**——見せて 403 にすると、現場は毎回そこで詰まる。
 */
function declare() {
  customsDeclarations.push({
    declarationId: 1,
    declarationNumber: "DEC-0001",
    bookingId: "BKG-2026000001",
    trackingNumber: "TRK-20260823-0001",
    declaredAt: "2027-09-02T09:00:00.000Z",
    status: "PENDING",
    clearedAt: null,
    remarks: null,
    history: [
      {
        fromStatus: "PENDING",
        toStatus: "PENDING",
        changedBy: "handler01",
        changedAt: "2027-09-02T09:00:00.000Z",
        reason: "申告を登録しました",
      },
    ],
  });
}

describe("通関申告の詳細（US29）", () => {
  beforeEach(() => {
    resetCustomsDeclarations();
    declare();
    server.use(...customsHandlers);
  });

  function renderPage() {
    renderWithProviders(<CustomsDetailPage />, ["/customs/1"], createTestQueryClient(), {
      path: "/customs/:declarationId",
    });
  }

  it("申告の中身が読める", async () => {
    loginAs(["ROLE_TRACKER"]);
    renderPage();

    expect(await screen.findByText("DEC-0001")).toBeInTheDocument();
    expect(screen.getByText("TRK-20260823-0001")).toBeInTheDocument();
    expect(screen.getByText("BKG-2026000001")).toBeInTheDocument();
  });

  /**
   * US29-2。**理由の入力は必須**。
   *
   * 空で通すと、監査の履歴が「誰かが変えた」だけになる。サーバも同じ規則を持つ
   * ——画面だけで守ると、API を直接叩けば理由なしの変更が入る。
   */
  it("理由を入れずには状態を更新できない", async () => {
    loginAs(["ROLE_TRACKER"]);
    renderPage();
    await screen.findByText("DEC-0001");

    expect(screen.getByLabelText("変更の理由")).toBeRequired();
  });

  it("状態を更新すると、履歴に日時・変更者・理由が残る", async () => {
    loginAs(["ROLE_TRACKER"]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("DEC-0001");

    await user.selectOptions(screen.getByLabelText("新しい状態"), "HELD");
    await user.type(screen.getByLabelText("変更の理由"), "書類不備");
    await user.click(screen.getByRole("button", { name: "状態を更新する" }));

    const history = within(
      await screen.findByRole("table", { name: "状態変更履歴" }),
    );
    expect(await history.findByText("書類不備")).toBeInTheDocument();
    expect(history.getByText("tracker01")).toBeInTheDocument();
    expect(history.getByText(/審査中.*留置/)).toBeInTheDocument();
  });

  /**
   * **押せない操作を見せない**（[ADR-025] 決定 6）。
   *
   * 荷役作業員は自分が出した申告の行方を追うために詳細を開くが、状態は更新できない。
   * 更新の枠を見せて 403 にすると、現場は毎回そこで詰まる。
   */
  it("荷役作業員には、状態を更新する枠が出ない", async () => {
    loginAs(["ROLE_HANDLER"]);
    renderPage();

    expect(await screen.findByText("DEC-0001")).toBeInTheDocument();
    expect(screen.queryByLabelText("新しい状態")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "状態を更新する" }),
    ).not.toBeInTheDocument();
  });

  /**
   * US29-4 は<strong>代替</strong>である（通知の仕組みがまだ無い）。
   *
   * <p><strong>送っていないことを画面が言う</strong>（IT8 と同じ形）。書かないと、
   * 追跡管理者は「通関済にしたから荷主に届いた」と受け取り、電話をしない。
   * 荷主は引き取りに来ない。
   */
  it("通関済にしても、通知は送っていないことを画面が言う", async () => {
    loginAs(["ROLE_TRACKER"]);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("DEC-0001");

    await user.selectOptions(screen.getByLabelText("新しい状態"), "CLEARED");
    await user.type(screen.getByLabelText("変更の理由"), "書類確認により通関完了");
    await user.click(screen.getByRole("button", { name: "状態を更新する" }));

    expect(
      await screen.findByText(/荷主・荷受人へのご連絡は自動では行われません/),
    ).toBeInTheDocument();
  });

  /** 通関済でないあいだは出さない。**まだ伝えることが無い**。 */
  it("審査中のあいだは、連絡の案内を出さない", async () => {
    loginAs(["ROLE_TRACKER"]);
    renderPage();

    await screen.findByText("DEC-0001");
    expect(
      screen.queryByText(/荷主・荷受人へのご連絡は自動では行われません/),
    ).not.toBeInTheDocument();
  });

  /** 登録も履歴に残る。**何も無い状態から始まらない**。 */
  it("登録そのものが履歴の最初の 1 行として残っている", async () => {
    loginAs(["ROLE_TRACKER"]);
    renderPage();

    const history = within(
      await screen.findByRole("table", { name: "状態変更履歴" }),
    );
    expect(history.getByText("申告を登録しました")).toBeInTheDocument();
    expect(history.getByText("handler01")).toBeInTheDocument();
  });
});
