import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  customsDeclarations,
  customsHandlers,
  resetCustomsDeclarations,
} from "../../mocks/handlers/customs";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";
import { CustomsPage } from "../customs-page";

/**
 * 通関申告の一覧（US29-6・US29-7）。
 *
 * 追跡管理者が毎朝この一覧を開く。**留め置かれている貨物から手を付けられる**ことが、
 * この画面の値打ちである。
 */
const DAY = 24 * 60 * 60 * 1000;

/** 留置に遷移してからの日数を指定して、申告を 1 件仕込む。 */
function heldDeclaration(
  declarationId: number,
  declarationNumber: string,
  daysHeld: number,
) {
  const heldAt = new Date(Date.now() - daysHeld * DAY).toISOString();
  customsDeclarations.push({
    declarationId,
    declarationNumber,
    bookingId: `BKG-202600000${declarationId}`,
    trackingNumber: `TRK-20260823-000${declarationId}`,
    declaredAt: heldAt,
    status: "HELD",
    clearedAt: null,
    remarks: null,
    history: [
      {
        fromStatus: "PENDING",
        toStatus: "HELD",
        changedBy: "tracker01",
        changedAt: heldAt,
        reason: "書類不備",
      },
    ],
  });
}

/**
 * 審査中の申告。
 *
 * <p>一覧の既定は<strong>未決着だけ</strong>なので、状態が本質でないテスト
 * （絞り込み・導線・ボタンの出し分け）はこちらを使う。
 */
function pendingDeclaration(declarationId: number, declarationNumber: string) {
  const at = new Date(Date.now() - DAY).toISOString();
  customsDeclarations.push({
    declarationId,
    declarationNumber,
    bookingId: `BKG-202600000${declarationId}`,
    trackingNumber: `TRK-20260823-000${declarationId}`,
    declaredAt: at,
    status: "PENDING",
    clearedAt: null,
    remarks: null,
    history: [],
  });
}

function clearedDeclaration(declarationId: number, declarationNumber: string) {
  const at = new Date(Date.now() - DAY).toISOString();
  customsDeclarations.push({
    declarationId,
    declarationNumber,
    bookingId: `BKG-202600000${declarationId}`,
    trackingNumber: `TRK-20260823-000${declarationId}`,
    declaredAt: at,
    status: "CLEARED",
    clearedAt: at,
    remarks: null,
    history: [],
  });
}

describe("通関申告の一覧（US29）", () => {
  beforeEach(() => {
    resetCustomsDeclarations();
    loginAs(["ROLE_TRACKER"]);
    // ブラウザ用モックと同じハンドラを使う。テスト用に別のものを組み立てると、
    // 本物との読み比べの対象が 1 つ増える
    server.use(...customsHandlers);
  });

  function renderPage() {
    renderWithProviders(<CustomsPage />);
  }

  /**
   * 決着済みも見る。
   *
   * <p>一覧の既定は<strong>未決着（審査中・留置）だけ</strong>である
   * ——追跡管理者の朝の仕事は「未決着を上から片付ける」ことであり、決着済みが混ざった
   * 一覧を毎朝すべて見直すことはできない。決着済みを確かめるテストは、
   * <strong>利用者と同じ操作で</strong>絞り込みを外す。
   */
  async function showSettledToo(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByLabelText("未決着（審査中・留置）だけ"));
    await user.click(screen.getByRole("button", { name: "検索" }));
  }

  it("申告が並び、状態が読める言葉で出る", async () => {
    clearedDeclaration(1, "DEC-0001");
    renderPage();
    await showSettledToo(userEvent.setup());

    // 状態の言葉は検索の選択肢にも出る。**行の中**で見る
    const row = within(
      (await screen.findByText("DEC-0001")).closest("tr") as HTMLElement,
    );
    expect(row.getByText("通関済")).toBeInTheDocument();
  });

  /**
   * **申告日時が、人の読む形で出る。**
   *
   * 生の ISO 文字列（`2027-09-03T00:00:00.000Z`）が出ると、利用者は自分の時刻に
   * 読み替えられない。IT8 で荷主の画面に同じことが起き、IT9 では
   * **モックだけが生の ISO を返していた**——画面のテストは日時の形を見ておらず、
   * マニュアルのキャプチャにその姿が載った。
   */
  it("申告日時が、業務の時刻として読める形で出る", async () => {
    clearedDeclaration(1, "DEC-0001");
    renderPage();
    await showSettledToo(userEvent.setup());

    const row = within(
      (await screen.findByText("DEC-0001")).closest("tr") as HTMLElement,
    );
    // YYYY-MM-DD HH:mm。T や Z を含まない
    expect(row.getByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)).toBeInTheDocument();
  });

  /**
   * US29-6。**留置 3 日超は警告が出る。**
   *
   * 3 日ちょうどでは出さない。「3 日を超えたら」であり、境界を入れると
   * 督促の対象が 1 日早まる。
   */
  it("留置のまま 3 日を超えた申告に警告が出る", async () => {
    heldDeclaration(1, "DEC-0001", 5);
    // **3 日ちょうどでは出さない。**境界を入れると督促の対象が 1 日早まる。
    // この 1 件が無いと、判定を「3 日以上」に緩めても検査は緑のままになる
    heldDeclaration(2, "DEC-0002", 3);
    heldDeclaration(3, "DEC-0003", 2);
    renderPage();

    const overdue = within(
      (await screen.findByText("DEC-0001")).closest("tr") as HTMLElement,
    );
    expect(overdue.getByText(/3 日超/)).toBeInTheDocument();

    const exactlyThreeDays = within(
      screen.getByText("DEC-0002").closest("tr") as HTMLElement,
    );
    expect(exactlyThreeDays.queryByText(/3 日超/)).not.toBeInTheDocument();

    const recent = within(
      screen.getByText("DEC-0003").closest("tr") as HTMLElement,
    );
    expect(recent.queryByText(/3 日超/)).not.toBeInTheDocument();
  });

  /**
   * **経過は、最新の HELD 遷移から数える**（data-model.md の注）。
   *
   * 申告日時から数えると、いったん通関して留め直された申告が、留め直した初日から
   * 「3 日超」と判定される。督促は「いま何日留め置かれているか」で決める。
   */
  it("いったん通関して留め直した申告は、留め直した日から数える", async () => {
    const declaredAt = new Date(Date.now() - 30 * DAY).toISOString();
    const heldAgainAt = new Date(Date.now() - 1 * DAY).toISOString();
    customsDeclarations.push({
      declarationId: 1,
      declarationNumber: "DEC-0001",
      bookingId: "BKG-2026000001",
      trackingNumber: "TRK-20260823-0001",
      declaredAt,
      status: "HELD",
      clearedAt: null,
      remarks: null,
      history: [
        {
          fromStatus: "PENDING",
          toStatus: "CLEARED",
          changedBy: "tracker01",
          changedAt: new Date(Date.now() - 29 * DAY).toISOString(),
          reason: "通関完了",
        },
        {
          fromStatus: "CLEARED",
          toStatus: "HELD",
          changedBy: "tracker01",
          changedAt: heldAgainAt,
          reason: "再検査",
        },
      ],
    });
    renderPage();

    const row = within(
      (await screen.findByText("DEC-0001")).closest("tr") as HTMLElement,
    );
    expect(row.queryByText(/3 日超/)).not.toBeInTheDocument();
    expect(row.getByText("1 日")).toBeInTheDocument();
  });

  /**
   * **件数から対象一覧へ辿れる**（横断規約）。
   *
   * 件数だけ出しても仕事は進まない。この画面では、警告の件数を先頭に出したうえで
   * 対象だけに絞り込める。
   */
  it("留置 3 日超の件数が出て、そこから対象だけに絞れる", async () => {
    heldDeclaration(1, "DEC-0001", 5);
    heldDeclaration(2, "DEC-0002", 2);
    renderPage();

    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: /3 日を超えた申告が 1 件/ }),
    );

    expect(await screen.findByText("DEC-0001")).toBeInTheDocument();
    expect(screen.queryByText("DEC-0002")).not.toBeInTheDocument();
  });

  /**
   * **絞り込んでも件数は消えない。**
   *
   * 画面に出ている行から数えると、「通関済」に絞った瞬間にバナーが消える。
   * 留置 3 日超は依然としてあるのに「無い」と見える——**絞り込んだら警告が消えた**は、
   * 警告そのものへの信用を失わせる。
   */
  it("別の条件で絞り込んでも、留置 3 日超の件数は出たままになる", async () => {
    heldDeclaration(1, "DEC-0001", 5);
    clearedDeclaration(2, "DEC-0002");
    renderPage();
    await screen.findByText("DEC-0001");

    const user = userEvent.setup();
    // 決着済みに絞る——**未決着の絞り込みも外す**（既定は未決着だけ）
    await user.click(screen.getByLabelText("未決着（審査中・留置）だけ"));
    await user.selectOptions(screen.getByLabelText("通関状態"), "CLEARED");
    await user.click(screen.getByRole("button", { name: "検索" }));

    expect(await screen.findByText("DEC-0002")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /3 日を超えた申告が 1 件/ }),
    ).toBeInTheDocument();
  });

  /** US29-7。貨物 ID・追跡番号・通関状態の 3 条件で絞れる。 */
  it("追跡番号で絞り込める", async () => {
    pendingDeclaration(1, "DEC-0001");
    pendingDeclaration(2, "DEC-0002");
    renderPage();
    await screen.findByText("DEC-0001");

    const user = userEvent.setup();
    await user.type(screen.getByLabelText("追跡番号"), "TRK-20260823-0002");
    await user.click(screen.getByRole("button", { name: "検索" }));

    expect(await screen.findByText("DEC-0002")).toBeInTheDocument();
    expect(screen.queryByText("DEC-0001")).not.toBeInTheDocument();
  });

  it("状態で絞り込める", async () => {
    pendingDeclaration(1, "DEC-0001");
    heldDeclaration(2, "DEC-0002", 1);
    renderPage();
    await screen.findByText("DEC-0001");

    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("通関状態"), "HELD");
    await user.click(screen.getByRole("button", { name: "検索" }));

    expect(await screen.findByText("DEC-0002")).toBeInTheDocument();
    expect(screen.queryByText("DEC-0001")).not.toBeInTheDocument();
  });

  /** 一覧を行き止まりにしない。申告番号から詳細へ進む。 */
  it("申告番号から詳細へ進める", async () => {
    pendingDeclaration(1, "DEC-0001");
    renderPage();

    expect(await screen.findByRole("link", { name: "DEC-0001" })).toHaveAttribute(
      "href",
      "/customs/1",
    );
  });

  /**
   * **[ADR-025] 決定 6。申告を出すのは荷役作業員だけ。**
   *
   * 追跡管理者は状態を更新する側であり、申告そのものは出さない。押せない操作を
   * 見せると、押した先で断られる。守るのはサーバであり、画面はその写しである。
   */
  it("追跡管理者には、新規申告のボタンが出ない", async () => {
    pendingDeclaration(1, "DEC-0001");
    renderPage();
    await screen.findByText("DEC-0001");

    expect(screen.queryByRole("link", { name: "新規申告" })).not.toBeInTheDocument();
  });

  it("荷役作業員には、新規申告のボタンが出る", async () => {
    loginAs(["ROLE_HANDLER"]);
    pendingDeclaration(1, "DEC-0001");
    renderPage();
    await screen.findByText("DEC-0001");

    expect(screen.getByRole("link", { name: "新規申告" })).toHaveAttribute(
      "href",
      "/customs/new",
    );
  });

  it("1 件も無ければ、その旨を出す", async () => {
    renderPage();

    expect(await screen.findByText("通関申告はありません。")).toBeInTheDocument();
  });

  /**
   * US29-7・IT10 返済枠 0.1。**朝の待ち行列。**
   *
   * <p>追跡管理者の朝の仕事は「未決着（審査中・留置）を上から片付ける」ことである。
   * 決着済みが混ざった一覧を毎朝すべて見直すことはできない。
   * <strong>状態の絞り込みは単一選択</strong>なので、審査中と留置を同時に見る手段が別に要る。
   */
  it("既定では、未決着（審査中・留置）だけが出る", async () => {
    pendingDeclaration(1, "DEC-0001");
    heldDeclaration(2, "DEC-0002", 1);
    clearedDeclaration(3, "DEC-0003");
    renderPage();

    expect(await screen.findByText("DEC-0001")).toBeInTheDocument();
    expect(screen.getByText("DEC-0002")).toBeInTheDocument();
    expect(
      screen.queryByText("DEC-0003"),
      "決着済みが混ざっている。毎朝の待ち行列にならない",
    ).not.toBeInTheDocument();
  });

  it("絞り込みを外すと、決着済みも出る", async () => {
    pendingDeclaration(1, "DEC-0001");
    clearedDeclaration(3, "DEC-0003");
    renderPage();
    await screen.findByText("DEC-0001");

    await showSettledToo(userEvent.setup());

    expect(await screen.findByText("DEC-0003")).toBeInTheDocument();
  });

  /**
   * <strong>上限で切ったことを黙らない。</strong>
   *
   * <p>件数を知らせずに切ると、担当者は「一覧に出ていないから無い」と読む。
   * 予約一覧と同じ形にする。
   */
  it("何件あるかが出る", async () => {
    pendingDeclaration(1, "DEC-0001");
    pendingDeclaration(2, "DEC-0002");
    renderPage();
    await screen.findByText("DEC-0001");

    expect(await screen.findByText(/^2 件/)).toBeInTheDocument();
  });
});
