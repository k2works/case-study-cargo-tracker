import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

/**
 * IT8 の受け入れ。**デモ項目 8 件をそのまま並べる**。
 *
 * 計画の「デモ項目」表と 1:1 で対応させる。表とテストがずれると、デモで見せるものと
 * 検査しているものが別になり、**通っていないことに気づけない**。
 *
 * US17（貨物状態の手動更新）・US18（追跡情報の照会）・US19（遅延例外）・
 * US20（破損・紛失例外）に対応する。
 *
 * IT8 のスコープ外:
 * - メールの送信（US17-4・US19-3・US20-4 は**代替**。送った事実を記録し、画面に出す。
 *   [ADR-024] 決定 9）
 * - `MISROUTE` / `CUSTOMS_HOLD` の起票（US28・IT10 / US29・IT9。[ADR-024] 決定 11）
 * - `ROLE_SHIPPER` への予約参照開放（US18 では果たせない。[ADR-024] 決定 10）
 * - 予定外の作業の待ち行列（US28・IT10）
 */

/** 種データの追跡番号（`src/mocks/data.ts` の BKG-2026000004）。 */
const TRACKING_NUMBER = "TRK-20260823-0001";

/** 実在しない追跡番号。形式は正しいものを使う——形式で断られては US18-4 を見たことにならない。 */
const UNKNOWN_TRACKING_NUMBER = "TRK-20260823-9999";

async function logIn(page: Page, userId: string) {
  await page.goto("/login");
  await page.getByLabel("利用者 ID").fill(userId);
  await page.getByLabel("パスワード").fill("password");
  await page.getByRole("button", { name: "ログイン" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

/** 追跡管理者の担当画面へ入る。**ダッシュボードから辿れること**もここで見る。 */
async function openTrackingManagement(page: Page) {
  await logIn(page, "tracker01");
  await page.getByRole("link", { name: "貨物の状態を管理する" }).click();
  await expect(
    page.getByRole("heading", { name: "貨物状態の管理" }),
  ).toBeVisible();
}

/**
 * 公開の追跡照会を、<strong>画面遷移だけで</strong>開く。
 *
 * <p><strong>読み込み直さない。</strong>モック（MSW）の状態はブラウザのメモリにあり、
 * ページを読み込み直すと消える。いま記録したものが追跡へ届いたかを見たいので、
 * 画面のリンクを辿る。
 *
 * <p><strong>ログインなしで開けること</strong>は、まっさらな状態から開くデモ 2 と
 * 「ロール別の到達性」が確かめる。<strong>別の端末から本当に見えること</strong>は、
 * kind の統合環境で確かめる（成功基準 1）——モックでは確かめようがない。
 */
async function lookUpByNavigating(page: Page, trackingNumber: string) {
  await page.getByRole("link", { name: "貨物追跡", exact: true }).click();
  await expect(page).toHaveURL(/\/tracking$/);
  // **入った値を確かめてから押す。**画面に入った直後は React が描き直す途中で
  // あり、fill した値がその再描画で捨てられることがある。押したあとに URL だけを
  // 見ると、空のまま送られたのか遷移が遅いのかを見分けられない（CI でだけ落ちた）
  const field = page.getByLabel("追跡番号");
  await expect(async () => {
    await field.fill(trackingNumber);
    await expect(field).toHaveValue(trackingNumber);
  }).toPass({ timeout: 10_000 });
  await page.getByRole("button", { name: "追跡する" }).click();
  await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}$`));
}

/** 追跡管理の画面で 1 件を開く。 */
async function showCargo(page: Page) {
  await page.getByLabel("追跡番号").fill(TRACKING_NUMBER);
  await page.getByRole("button", { name: "貨物を表示する" }).click();
  await expect(
    page.getByRole("heading", { name: TRACKING_NUMBER }),
  ).toBeVisible();
}

/** 状態を手で反映する。選択肢が届くのを待ってから選ぶ。 */
async function updateStatus(page: Page, status: string) {
  await expect(page.getByRole("option", { name: "受領済み" })).toHaveCount(1);
  await page.getByLabel("新しい状態").selectOption(status);
  await page.getByLabel("現在地").selectOption("JPTYO");
  await page.getByLabel("日時").fill("2027-09-03T09:00");
  await page.getByRole("button", { name: "状態を更新する" }).click();
}

/** 例外を起票する。選択肢が届くのを待ってから選ぶ。 */
async function raiseException(page: Page, type: string, description: string) {
  await page.getByRole("button", { name: "例外を起票する" }).click();
  await expect(page.getByRole("option", { name: "遅延" })).toHaveCount(1);
  await page.getByLabel("例外の種別").selectOption(type);
  await page.getByLabel("発生状況").fill(description);
  await page.getByRole("button", { name: "起票する" }).click();
}

/** 公開の追跡照会を、まっさらな状態から開く（ログインしていない）。 */
async function lookUp(page: Page, trackingNumber: string) {
  await page.goto("/");
  await page.getByLabel("追跡番号").fill(trackingNumber);
  await page.getByRole("button", { name: "追跡する" }).click();
}

test.describe("デモ項目（IT8）", () => {
  /**
   * デモ 1。US15・US18。**このイテレーションの中心**である。
   *
   * IT7 までは、追跡の状態を見られるのは DB とデモだけだった。
   */
  test("1. 荷役を記録すると、荷主がログインなしで状態・位置・履歴を見られる", async ({
    page,
  }) => {
    await logIn(page, "handler01");
    await page.getByRole("link", { name: "荷役作業を記録する" }).click();
    await page.getByLabel("追跡番号").fill(TRACKING_NUMBER);
    await page.getByLabel("作業の種別").selectOption("RECEIVE");
    await page.getByLabel("作業場所").selectOption("JPTYO");
    await page.getByLabel("作業日時").fill("2027-09-02T09:00");
    await page.getByRole("button", { name: "記録する" }).click();
    await expect(page.getByText("記録しました。")).toBeVisible();

    await lookUpByNavigating(page, TRACKING_NUMBER);

    // いまの状態（見出しの下の要約）と、経過の表の両方に出る
    await expect(page.getByText("受領済み").first()).toBeVisible();
    await expect(page.getByText("Tokyo").first()).toBeVisible();
    // 履歴（US18-3）。荷役の記録と手動更新の両方が並ぶ
    await expect(page.getByRole("table")).toContainText("受領済み");
    // 推定到着日（US18-2）。**値まで見る**——ラベルの存在だけを見ると、
    // 「未定」と出ていても緑になる
    await expect(page.getByText("2027-09-15")).toBeVisible();

    // **返さないものは出さない**（[ADR-024] 決定 5）
    const body = await page.locator("body").innerText();
    expect(body, "予約番号が荷主に見えている").not.toContain("BKG-");
    expect(body, "作業者が荷主に見えている").not.toContain("handler01");
  });

  /** デモ 2。US18-4。 */
  test("2. 存在しない追跡番号は「見つかりません」と出る", async ({ page }) => {
    await lookUp(page, UNKNOWN_TRACKING_NUMBER);

    await expect(page.getByText("追跡番号が見つかりません")).toBeVisible();
    // **行き止まりにしない。**打ち直せる場所が同じ画面にある
    await expect(page.getByLabel("追跡番号")).toBeVisible();
  });

  /**
   * デモ 3。US17。**荷役では捕捉できない状態変化**を手で反映する。
   *
   * 出港（`ONBOARD_CARRIER`）は荷役の記録では起きない——船が出たことは港の作業ではない。
   */
  test("3. 追跡管理者が出港を手で反映すると、公開画面に出る", async ({ page }) => {
    await openTrackingManagement(page);
    await showCargo(page);

    await updateStatus(page, "ONBOARD_CARRIER");
    await expect(page.getByText("更新しました。")).toBeVisible();

    await lookUpByNavigating(page, TRACKING_NUMBER);

    await expect(page.getByText("輸送中").first()).toBeVisible();
  });

  /**
   * デモ 4。US17-2・[ADR-024] 決定 1。
   *
   * **手動だから自由に動かせる、とはしない。** 荷主が見ているのは 1 本の状態であり、
   * どの入口から動いたかは荷主に見えない。手動経路にだけ抜け道を作ると、IT7 で塞いだ
   * 巻き戻りが人の操作で起きる。
   */
  test("4. 戻る向きには、そもそも動かせない", async ({ page }) => {
    await openTrackingManagement(page);
    await showCargo(page);
    await updateStatus(page, "ONBOARD_CARRIER");
    await expect(page.getByText("更新しました。")).toBeVisible();

    // **押せるのに断られる操作を出さない**（[ADR-024] 決定 1）。
    // 進める先の選択肢はサーバが返すので、戻る向きは選択肢そのものが無い
    await expect(
      page.getByRole("option", { name: "受領待ち" }),
    ).toHaveCount(0);
    await expect(page.getByRole("option", { name: "受領済み" })).toHaveCount(0);
    // **直す手段は画面に書いてある。**「できません」で終わらせない
    await expect(page.getByText(/前の状態には戻せません/)).toBeVisible();
  });

  /** デモ 5。US19-1・US19-2。 */
  test("5. 遅延を起票すると、状態が「例外発生」になる", async ({ page }) => {
    await openTrackingManagement(page);
    await showCargo(page);

    await raiseException(page, "DELAY", "台風により出港が 2 日遅れています");

    await expect(page.getByText("起票しました。")).toBeVisible();
    await expect(page.getByText("例外発生").first()).toBeVisible();
  });

  /**
   * デモ 6。US19-4・[ADR-024] 決定 2。
   *
   * **発生前の状態に戻る。** 履歴から再導出すると、ユニット緑のまま誤復帰する。
   * ここは**画面を開き直してから**解決する——同じリクエストの中で解決すると、
   * 行に残っていないことに気づけない。
   */
  test("6. 例外を解決すると、発生前の状態に戻る", async ({ page }) => {
    await openTrackingManagement(page);
    await showCargo(page);
    // 出港まで進めてから起票する。**受領待ちへ巻き戻らないこと**を見たい
    await updateStatus(page, "ONBOARD_CARRIER");
    await expect(page.getByText("更新しました。")).toBeVisible();
    await raiseException(page, "DELAY", "台風により出港が 2 日遅れています");
    await expect(page.getByText("起票しました。")).toBeVisible();

    await page.getByRole("button", { name: "解決する" }).click();
    await page.getByLabel("対応内容").fill("別便に振り替えました");
    await page.getByLabel("新しい到着予定日").fill("2027-09-20");
    await page.getByRole("button", { name: "解決を記録する" }).click();

    await expect(page.getByText("解決しました。")).toBeVisible();
    // **発生前の状態に戻る。**受領待ちへは戻らない（対で見る）
    await expect(page.getByText("輸送中").first()).toBeVisible();
    await expect(page.getByText("例外発生")).toHaveCount(0);
    // US19-4。新しい到着予定日が反映される
    await expect(page.getByText("2027-09-20")).toBeVisible();
  });

  /**
   * デモ 7。US20-3・[ADR-024] 決定 3。
   *
   * **対で確かめる。**「紛失で立つ」だけを見ると、常に真を返す実装でも緑になる。
   */
  test("7. 紛失だけが緊急として扱われ、破損では立たない", async ({ page }) => {
    await openTrackingManagement(page);
    await showCargo(page);

    await raiseException(page, "DAMAGE", "外装に破損があります");
    await expect(page.getByText("起票しました。")).toBeVisible();
    await expect(page.getByText("緊急")).toHaveCount(0);

    // 未解決の例外は 1 件まで。先に解決してから紛失を起票する
    await page.getByRole("button", { name: "解決する" }).click();
    await page.getByLabel("対応内容").fill("再梱包しました");
    await page.getByRole("button", { name: "解決を記録する" }).click();
    await expect(page.getByText("解決しました。")).toBeVisible();

    await raiseException(page, "LOST", "積替港で所在が確認できません");

    await expect(page.getByText("緊急").first()).toBeVisible();
  });

  /**
   * デモ 8。US17-4・US19-3・US20-4・[ADR-024] 決定 9。
   *
   * **代替であることを画面に書く。** 書かないと、荷主は「メールが来ないのは不具合」と
   * 受け取る。US12・US15-5 と同じ形。
   */
  test("8. 通知が送られていないことを、荷主の画面が言っている", async ({
    page,
  }) => {
    await lookUp(page, TRACKING_NUMBER);

    // 通知したという事実は残り、画面に出る
    await expect(page.getByRole("heading", { name: "お知らせ" })).toBeVisible();
    await expect(page.getByText(/メールは送っていません/)).toBeVisible();
  });
});

/**
 * **ロール別の到達性**（[IT7 Try 4](../../docs/development/retrospective-7.md)）。
 *
 * IT7 は追跡管理者が `/handling` を開いても何もできなかった。メニューに出すのは、
 * そのロールで**何かできる**画面に限る。
 */
test.describe("ロール別の到達性", () => {
  test("追跡管理者は、ダッシュボードから状態管理へ行き、そこで起票できる", async ({
    page,
  }) => {
    await openTrackingManagement(page);

    await expect(
      page.getByRole("button", { name: "貨物を表示する" }),
    ).toBeEnabled();
  });

  /**
   * **認証不要の画面は、入口も認証の外に置く**（IT7 の学び）。
   *
   * ロール別到達性は認証済み利用者にしか働かない。荷主はログインしない。
   */
  test("荷主は、ポータルとログイン画面の両方から追跡照会へ行ける", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByLabel("追跡番号")).toBeEnabled();

    await page.goto("/login");
    await expect(page.getByRole("link", { name: /追跡/ })).toBeVisible();
  });

  /**
   * IT10 レビュー（user-representative 高 1・高 2）。
   *
   * <p>誤配に気づくのも、キャンセルを承認するのも追跡管理者である。どちらの一覧からも
   * 予約詳細へ渡す導線を置いたが、**押すと /403 に飛んでいた**。画面単体のテストは
   * ルートガードを通らないため、この欠陥を判別しない。**ここでは実際に踏む。**
   */
  test("追跡管理者は、予約の詳細を読める（操作は出ない）", async ({ page }) => {
    await logIn(page, "tracker01");

    await page.goto("/booking/BKG-2026000001");
    await expect(page).not.toHaveURL(/\/403/);
    await expect(page.getByRole("heading", { name: /BKG-2026000001/ })).toBeVisible();
    // 読むだけ。操作は出さない——出すと、押した先でサーバに断られる
    await expect(
      page.getByRole("button", { name: /経路を割り当て|確定する/ }),
    ).toHaveCount(0);
  });

  /**
   * **一覧までは開かない。** 例外や承認から辿る 1 件を読むことと、営業の抱えている
   * 案件を横断して眺めることは別である。
   */
  test("追跡管理者に予約の一覧は開かない", async ({ page }) => {
    await logIn(page, "tracker01");

    await page.goto("/booking");
    await expect(page).toHaveURL(/\/403/);
  });
});
