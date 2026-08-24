import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  customsDeclarations,
  customsHandlers,
  resetCustomsDeclarations,
} from "../../mocks/handlers/customs";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";
import { CustomsNewPage } from "../customs-new-page";

/**
 * 通関申告の登録（US29-1）。
 *
 * **荷役作業員が使う。** 荷役作業員は追跡番号を起点に作業しており、予約番号は知らない
 * （[ADR-023] 決定 2 と同じ立場）。
 */
const TRACKING_NUMBER = "TRK-20260823-0001";

describe("通関申告の登録（US29-1）", () => {
  beforeEach(() => {
    resetCustomsDeclarations();
    loginAs(["ROLE_HANDLER"]);
    server.use(...customsHandlers);
  });

  function renderPage() {
    renderWithProviders(<CustomsNewPage />);
  }

  async function fillAndSubmit(
    user: ReturnType<typeof userEvent.setup>,
    overrides: Partial<{
      trackingNumber: string;
      declarationNumber: string;
      declaredAt: string;
    }> = {},
  ) {
    // 登録に成功すると申告番号だけが空になり、追跡番号と日時は残る（同じ貨物に
    // 続けて出すことがあるため）。入れ直す前に必ず消す——消さないと文字が継ぎ足される
    await user.clear(screen.getByLabelText("追跡番号"));
    await user.type(
      screen.getByLabelText("追跡番号"),
      overrides.trackingNumber ?? TRACKING_NUMBER,
    );
    await user.clear(screen.getByLabelText("申告番号"));
    await user.type(
      screen.getByLabelText("申告番号"),
      overrides.declarationNumber ?? "DEC-0001",
    );
    await user.clear(screen.getByLabelText("申告日時"));
    await user.type(
      screen.getByLabelText("申告日時"),
      overrides.declaredAt ?? "2027-09-02T09:00",
    );
    await user.click(screen.getByRole("button", { name: "登録する" }));
  }

  /** 初期状態はサーバが決める（PENDING）。画面が状態を選ばせない。 */
  it("登録すると審査中で記録され、状態は選ばせない", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.queryByLabelText("通関状態")).not.toBeInTheDocument();

    await fillAndSubmit(user);

    expect(await screen.findByText(/登録しました/)).toBeInTheDocument();
    expect(customsDeclarations[0].status).toBe("PENDING");
  });

  /**
   * **[ADR-025] 決定 7。未決着の申告は高々 1 件。**
   *
   * 2 件あると、引取（CLAIM）のガードがどちらの申告を見ればよいか決まらない。
   * 断る理由を画面に出す——「登録できません」だけでは、作業員は同じ操作を繰り返す。
   */
  it("決着していない申告があるあいだは、2 件目を登録できない", async () => {
    const user = userEvent.setup();
    renderPage();
    await fillAndSubmit(user);
    await screen.findByText(/登録しました/);

    await fillAndSubmit(user, { declarationNumber: "DEC-0002" });

    expect(
      await screen.findByText(/決着していない通関申告があります/),
    ).toBeInTheDocument();
    expect(customsDeclarations).toHaveLength(1);
  });

  /** 打ち間違いは最も多い。何を直せばよいかを出す。 */
  it("知らない追跡番号は、理由が画面に出る", async () => {
    const user = userEvent.setup();
    renderPage();

    await fillAndSubmit(user, { trackingNumber: "TRK-20260823-9999" });

    expect(
      await screen.findByText(/指定された追跡番号の貨物が見つかりません/),
    ).toBeInTheDocument();
  });

  /** 申告番号は業務キーである。空で通すと、あとから申告を特定できない。 */
  it("申告番号を空では送れない", async () => {
    renderPage();

    expect(screen.getByLabelText("申告番号")).toBeRequired();
  });

  it("申告日時を空では送れない", async () => {
    renderPage();

    expect(screen.getByLabelText("申告日時")).toBeRequired();
  });
});
