import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  customsDeclarations,
  customsHandlers,
  resetCustomsDeclarations,
} from "../../mocks/handlers/customs";
import {
  handlingActivities,
  handlingHandlers,
} from "../../mocks/handlers/handling";
import { server } from "../../test/msw/server";
import { loginAs, renderWithProviders } from "../../test/render";
import { HandlingPage } from "../handling-page";

/**
 * 荷役作業の記録（US15・US16）。
 *
 * 荷役作業員は追跡番号を起点に作業する。予約番号は知らない。
 */
/** 種別の表示名はサーバが持つ。ここは「選択肢が届いたか」を待つためだけに使う。 */
const TYPE_LABELS: Record<string, string> = {
  RECEIVE: "受領",
  LOAD: "積込",
  UNLOAD: "荷降し",
  CLAIM: "引取",
};

describe("荷役作業の記録（US15）", () => {
  /**
   * 通関済の申告を 1 件用意する。
   *
   * **引取は通関が下りていないと記録できない**（US29-3）。ここを省くと、
   * 引取の検査が「通関ガードに阻まれて記録されない」ことを見てしまい、
   * 確かめたい荷受人の確認の話にならない。
   */
  function clearCustoms(trackingNumber = "TRK-20260823-0001") {
    customsDeclarations.push({
      declarationId: 1,
      declarationNumber: "DEC-0001",
      bookingId: "BKG-2026000004",
      trackingNumber,
      declaredAt: "2027-09-01T00:00:00.000Z",
      status: "CLEARED",
      clearedAt: "2027-09-02T00:00:00.000Z",
      remarks: null,
      history: [],
    });
  }

  beforeEach(() => {
    handlingActivities.length = 0;
    resetCustomsDeclarations();
    loginAs(["ROLE_HANDLER"]);
    // ブラウザ用モックと同じハンドラを使う。テスト用に別のものを組み立てると、
    // 本物との読み比べ（IT5 Try 4）の対象が 1 つ増える
    server.use(...handlingHandlers, ...customsHandlers);
  });

  function renderPage() {
    return renderWithProviders(<HandlingPage />, ["/handling"], undefined, {
      path: "/handling",
    });
  }

  /** 種別の選択肢はサーバが返す。届く前に選ぼうとしても、まだ選択肢が無い。 */
  async function selectType(
    user: ReturnType<typeof userEvent.setup>,
    type: string,
  ) {
    await screen.findByRole("option", { name: TYPE_LABELS[type] });
    await user.selectOptions(screen.getByLabelText(/作業の種別/), type);
  }

  async function fillAndSubmit(overrides: Record<string, string> = {}) {
    const user = userEvent.setup();
    await user.type(
      await screen.findByLabelText("追跡番号"),
      overrides.trackingNumber ?? "TRK-20260823-0001",
    );
    await selectType(user, overrides.type ?? "RECEIVE");
    await user.selectOptions(
      screen.getByLabelText(/作業場所/),
      overrides.location ?? "JPTYO",
    );
    fireEvent.change(screen.getByLabelText(/作業日時/), {
      target: { value: overrides.completionTime ?? "2027-09-02T09:00" },
    });
    if (overrides.voyageNumber !== undefined) {
      await user.type(
        screen.getByLabelText(/航海番号/),
        overrides.voyageNumber,
      );
    }
    if (overrides.consigneeConfirmation !== undefined) {
      await user.type(
        screen.getByLabelText(/荷受人の確認/),
        overrides.consigneeConfirmation,
      );
    }
    await user.click(screen.getByRole("button", { name: "記録する" }));
    // 引取だけは確認を挟む（IT8 返済枠 0.9）。ここでは押し切って、結果を見る
    const confirm = screen.queryByRole("button", {
      name: "この貨物の引取を記録する",
    });
    if (confirm !== null) {
      await user.click(confirm);
    }
    return user;
  }

  /** US15-1〜US15-4。 */
  it("追跡番号で貨物を特定して受領を記録できる", async () => {
    renderPage();

    await fillAndSubmit();

    expect(await screen.findByText(/記録しました/)).toBeInTheDocument();
    // 履歴に出ることで、作業員は登録できたかが分かる
    const history = await screen.findByRole("table");
    expect(within(history).getByText("受領")).toBeInTheDocument();
    expect(within(history).getByText(/Tokyo/)).toBeInTheDocument();
  });

  /**
   * **登録後も追跡番号を残す。**
   *
   * 同じ貨物に続けて記録するのが荷役の実際の使い方である。全部空にすると、
   * 作業員は追跡番号を毎回打ち直すことになる。
   */
  it("記録したあとも追跡番号は残る", async () => {
    renderPage();

    await fillAndSubmit();
    await screen.findByText(/記録しました/);

    expect(await screen.findByLabelText("追跡番号")).toHaveValue(
      "TRK-20260823-0001",
    );
  });

  /** US15-6。番号を読み違えるのが最も多い。何を直せばよいかを伝える。 */
  it("存在しない追跡番号は理由を出す", async () => {
    renderPage();

    await fillAndSubmit({ trackingNumber: "TRK-99999999-9999" });

    expect(
      await screen.findByText(/番号を確かめてください/),
    ).toBeInTheDocument();
  });

  /**
   * US15-7・[ADR-023] 決定 3。
   *
   * **警告は出すが記録は拒まない。** 現場ではすでに作業が終わっており、拒むと実際に
   * 起きたことがどこにも残らない。
   */
  it("予定ルート外の作業は、警告を出したうえで記録に残る", async () => {
    renderPage();

    await fillAndSubmit({
      type: "UNLOAD",
      location: "SGSIN",
      voyageNumber: "V-SEED-3",
    });

    expect(await screen.findByText(/予定と違う場所/)).toBeInTheDocument();
    const history = await screen.findByRole("table");
    expect(within(history).getByText("荷降し")).toBeInTheDocument();
    expect(within(history).getByText(/予定外/)).toBeInTheDocument();
  });

  /** US15-2。要件はサーバが答える。画面は結果を出すだけ。 */
  it("積込を選ぶと航海番号の入力欄が出る", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.queryByLabelText(/航海番号/)).not.toBeInTheDocument();
    await selectType(user, "LOAD");

    expect(await screen.findByLabelText(/航海番号/)).toBeInTheDocument();
  });

  describe("引取（US16）", () => {
    /** US16-1。 */
    it("引取を選ぶと荷受人確認の欄が出る", async () => {
      const user = userEvent.setup();
      renderPage();

      expect(screen.queryByLabelText(/荷受人の確認/)).not.toBeInTheDocument();
      await selectType(user, "CLAIM");

      expect(await screen.findByLabelText(/荷受人の確認/)).toBeInTheDocument();
    });

    /**
     * US16-2・成功基準 3（画面層）。
     *
     * 通関ガード（US29・IT9）が無い IT7 では、これが唯一の歯止めである。
     */
    it("荷受人の確認がないと引取は記録できない", async () => {
      renderPage();

      await fillAndSubmit({ type: "CLAIM", location: "USLAX" });

      expect(
        await screen.findByText(/荷受人の確認は必須です/),
      ).toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    /** US16-3。 */
    it("荷受人の確認を入れると引取が記録される", async () => {
      clearCustoms();
      renderPage();

      await fillAndSubmit({
        type: "CLAIM",
        location: "USLAX",
        consigneeConfirmation: "山田太郎（受取担当）",
      });

      const history = await screen.findByRole("table");
      expect(within(history).getByText("引取")).toBeInTheDocument();
    });

    /**
     * **引取は取り消せない**（IT8 返済枠 0.9）。
     *
     * 記録後も種別が残るため、次の貨物に「引取」のまま記録する事故が起きうる。
     * 1 日数十件を打つ画面なので、確認は引取にだけ挟む。
     */
    it("引取は、確認するまで記録されない", async () => {
      clearCustoms();
      const user = userEvent.setup();
      renderPage();

      await user.type(
        await screen.findByLabelText("追跡番号"),
        "TRK-20260823-0001",
      );
      await selectType(user, "CLAIM");
      await user.selectOptions(screen.getByLabelText(/作業場所/), "USLAX");
      fireEvent.change(screen.getByLabelText(/作業日時/), {
        target: { value: "2027-09-02T09:00" },
      });
      await user.type(
        screen.getByLabelText(/荷受人の確認/),
        "山田太郎（受取担当）",
      );

      await user.click(screen.getByRole("button", { name: "記録する" }));

      expect(
        await screen.findByText(/引取の記録は取り消せません/),
      ).toBeInTheDocument();
      // どの貨物を引き渡すのかを、番号で見せる
      expect(screen.getByText("TRK-20260823-0001")).toBeInTheDocument();
      expect(screen.queryByText("記録しました。")).not.toBeInTheDocument();

      await user.click(
        screen.getByRole("button", { name: "この貨物の引取を記録する" }),
      );

      expect(await screen.findByText("記録しました。")).toBeInTheDocument();
    });

    /**
     * **事故の源を断つ。** 確認を出すより先に、種別が前の貨物のまま残らないようにする。
     */
    it("追跡番号を変えると、種別は受領に戻る", async () => {
      const user = userEvent.setup();
      renderPage();

      await user.type(
        await screen.findByLabelText("追跡番号"),
        "TRK-20260823-0001",
      );
      await selectType(user, "CLAIM");
      expect(screen.getByLabelText(/作業の種別/)).toHaveValue("CLAIM");

      await user.type(screen.getByLabelText("追跡番号"), "X");

      expect(screen.getByLabelText(/作業の種別/)).toHaveValue("RECEIVE");
    });

    /**
     * US29-3。**通関が下りていない貨物は引き取れない。**
     *
     * ドメインの守りは、**画面から踏むテストと対にする**（過去 take の教訓）。
     * 集約やユースケースの検査だけでは、画面での見え方（500 になっていないか、
     * 何が起きたか読めるか）を判別しない。
     *
     * **現在の通関状態を出す。**「できません」だけでは、作業員は次にすることが
     * 分からず、通関の担当者を探して電話することになる。
     */
    it("通関が留置のままなら、引取は断られ、いまの状態が画面に出る", async () => {
      customsDeclarations.push({
        declarationId: 1,
        declarationNumber: "DEC-0001",
        bookingId: "BKG-2026000004",
        trackingNumber: "TRK-20260823-0001",
        declaredAt: "2027-09-01T00:00:00.000Z",
        status: "HELD",
        clearedAt: null,
        remarks: null,
        history: [],
      });
      renderPage();

      await fillAndSubmit({
        type: "CLAIM",
        location: "USLAX",
        consigneeConfirmation: "山田太郎（受取担当）",
      });

      expect(
        await screen.findByText(/通関が完了していないため引き取りできません（現在: 留置）/),
      ).toBeInTheDocument();
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    /**
     * **申告が 1 件も無い貨物も断る。**
     *
     * 名簿方式の検査は「載っていないもの」を通すと、載せ忘れたものほど漏れる。
     * 申告が無いのは「検査の対象外」ではなく「通関済でない」である。
     */
    it("通関申告が無い貨物は、引取が断られる", async () => {
      renderPage();

      await fillAndSubmit({
        type: "CLAIM",
        location: "USLAX",
        consigneeConfirmation: "山田太郎（受取担当）",
      });

      expect(
        await screen.findByText(/この貨物には通関申告がありません/),
      ).toBeInTheDocument();
    });

    /**
     * **但し書きは外す**（[ADR-025] 決定 9）。
     *
     * IT9 で通関ガードが入った。「通関の確認は仕組みでは行われません」という文は
     * **誤りになる**——読んだ作業員は書類を目視で確かめ続け、システムが断ることを
     * 知らないまま二重に手間をかける。
     *
     * **文言の不在を見る。** 消したつもりで残っている形を、これで捕まえる
     * （[ADR-023] 決定 4 は「消し忘れるほうを踏むな」と予告していた）。
     */
    it("通関を仕組みで確かめていない、とは書かない", async () => {
      const user = userEvent.setup();
      renderPage();

      await selectType(user, "CLAIM");

      // 荷受人の確認そのものは残る（US16 の受入基準）
      expect(await screen.findByLabelText(/荷受人の確認/)).toBeInTheDocument();
      expect(
        screen.queryByText(/仕組みでは行われません/),
      ).not.toBeInTheDocument();
      expect(screen.queryByText(/通関の書類を確かめてから/)).not.toBeInTheDocument();
    });
  });

  /**
   * **荷主への通知はまだ行われない**（US15-5 は代替・IT8 の US19）。
   *
   * 書かないと、作業員は「記録すれば荷主に伝わる」と受け取る。
   */
  it("荷主へ自動で通知されないことを画面に書く", async () => {
    renderPage();

    expect(
      await screen.findByText(/荷主へは自動で通知されません/),
    ).toBeInTheDocument();
  });

  /**
   * <strong>作業日時は「いま」から始める。</strong>
   *
   * 港の記録はほぼ「いま」であり、1 日数十件を打つ人にとって一番手数の多い欄である。
   */
  it("作業日時には、はじめから「いま」が入っている", async () => {
    renderPage();

    expect(await screen.findByLabelText(/作業日時/)).not.toHaveValue("");
  });

  /**
   * <strong>読めない日時で送信そのものを止めない。</strong>
   *
   * 止めると画面には何も出ず、利用者からは「押しても何も起きない」に見える。
   */

  /** **気づく手段は次の行動へ繋ぐ。** 誰に連絡するのかを書く。 */
  it("予定外の警告は、誰に連絡するかを示す", async () => {
    renderPage();

    await fillAndSubmit({
      type: "UNLOAD",
      location: "SGSIN",
      voyageNumber: "V-SEED-3",
    });

    expect(await screen.findByText(/追跡管理者/)).toBeInTheDocument();
  });

  describe("追跡管理者として", () => {
    beforeEach(() => {
      loginAs(["ROLE_TRACKER"]);
    });

    /**
     * <strong>押せるのに断られる操作を出さない。</strong>
     *
     * サーバは追跡管理者の記録を 403 で断る。ボタンを出すと、押した先で断られる。
     */
    it("記録のフォームは出ない", async () => {
      renderPage();

      expect(
        await screen.findByRole("heading", { name: "荷役作業の記録" }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "記録する" }),
      ).not.toBeInTheDocument();
    });

    /**
     * <strong>メニューに出す以上、そこで何かできる。</strong>
     *
     * 追跡管理者が手元に持つのは追跡番号である。「あの貨物はもう積んだか」に
     * 答えられないと、この画面を開く意味が無い。
     */
    it("追跡番号だけで履歴を見られる", async () => {
      const user = userEvent.setup();
      // 荷役作業員が 1 件記録しておく（追跡管理者は記録できない）
      handlingActivities.push({
        id: 1,
        bookingId: "BKG-2026000004",
        type: "RECEIVE",
        locationUnLocode: "JPTYO",
        locationName: "Tokyo",
        completionTime: "2027-09-02T00:00:00Z",
        operatorName: "handler01",
        voyageNumber: null,
        consigneeConfirmation: null,
        offRoute: false,
      });
      renderPage();

      await user.type(
        await screen.findByLabelText("追跡番号"),
        "TRK-20260823-0001",
      );
      await user.click(screen.getByRole("button", { name: "履歴を見る" }));

      const history = await screen.findByRole("table");
      expect(within(history).getByText("受領")).toBeInTheDocument();
    });

    it("知らない追跡番号の履歴は、理由を出す", async () => {
      const user = userEvent.setup();
      renderPage();

      await user.type(
        await screen.findByLabelText("追跡番号"),
        "TRK-99999999-9999",
      );
      await user.click(screen.getByRole("button", { name: "履歴を見る" }));

      expect(
        await screen.findByText(/番号を確かめてください/),
      ).toBeInTheDocument();
    });
  });
});
