import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

/** US33: 荷主の担当者が、自社の貨物だけを追跡する。 */
const OWN_TRACKING_NUMBER = "TRK-20260823-0001";
const OTHER_TRACKING_NUMBER = "TRK-20260823-9001";

async function logIn(page: Page, userId: string) {
  await page.goto("/login");
  await page.getByLabel("利用者 ID").fill(userId);
  await page.getByLabel("パスワード").fill("password");
  await page.getByRole("button", { name: "ログイン" }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

test.describe("荷主向け貨物追跡（IT13 / US33）", () => {
  test("1. 荷主は自社貨物だけの一覧を見られる", async ({ page }) => {
    await logIn(page, "shipper01");
    await page.getByRole("link", { name: "自分の貨物を見る" }).click();

    await expect(page).toHaveURL(/\/shipper\/tracking$/);
    await expect(page.getByRole("heading", { name: "自分の貨物" })).toBeVisible();
    await expect(page.getByRole("link", { name: OWN_TRACKING_NUMBER })).toBeVisible();
    await expect(page.getByText(OTHER_TRACKING_NUMBER)).toHaveCount(0);
  });

  test("2. 一覧には状態・現在地・到着予定・例外が出る", async ({ page }) => {
    await logIn(page, "shipper03");
    await page.goto("/shipper/tracking");

    const row = page.getByRole("row", { name: new RegExp(OWN_TRACKING_NUMBER) });
    await expect(row).toContainText("例外発生");
    await expect(row).toContainText("Tokyo");
    await expect(row).toContainText("2027-09-15");
    await expect(row).toContainText("例外あり");
  });

  test("3. 自社貨物の詳細だけを開け、他社貨物は 404 の案内になる", async ({ page }) => {
    await logIn(page, "shipper01");
    await page.goto(`/shipper/tracking/${OWN_TRACKING_NUMBER}`);

    await expect(page.getByRole("heading", { name: OWN_TRACKING_NUMBER })).toBeVisible();
    await expect(page.getByRole("heading", { name: "これまでの経過" })).toBeVisible();

    await page.goto(`/shipper/tracking/${OTHER_TRACKING_NUMBER}`);
    await expect(page.getByRole("alert")).toContainText("自社の貨物として確認できません");
    await expect(page.getByRole("heading", { name: OTHER_TRACKING_NUMBER })).toHaveCount(0);
  });

  test("4. 紐付いていない荷主利用者には問い合わせ先を出す", async ({ page }) => {
    await logIn(page, "shipper02");
    await page.goto("/shipper/tracking");

    await expect(page.getByText("荷主との紐付けがありません")).toBeVisible();
    await expect(page.getByText(/営業担当またはシステム管理者/)).toBeVisible();
    await expect(page.getByText("自社貨物はありません。")).toHaveCount(0);
  });
});

test.describe("無操作タイムアウト（IT13 / TD-01）", () => {
  test("15 分無操作で警告し、入力中の業務フォームを隠さない", async ({ page }) => {
    await page.clock.install();
    await logIn(page, "sales01");
    await page.goto("/booking/new");
    await page.getByLabel("重量（kg）").fill("12000");

    await page.clock.fastForward(15 * 60 * 1000);

    await expect(page.getByRole("alert")).toContainText("まもなく自動ログアウトします");
    await expect(page.getByLabel("重量（kg）")).toBeVisible();
    await expect(page.getByLabel("重量（kg）")).toHaveValue("12000");
    await page.screenshot({ path: "test-results/it13-timeout-warning-booking-form.png", fullPage: true });
  });

  test("20 分無操作でログイン画面へ戻り、業務 API 呼び出しは認証切れになる", async ({
    page,
  }) => {
    await page.clock.install();
    await logIn(page, "shipper01");
    await page.goto("/shipper/tracking");
    await expect(page.getByRole("heading", { name: "自分の貨物" })).toBeVisible();

    await page.clock.fastForward(20 * 60 * 1000);

    await expect(page).toHaveURL(/\/login/);
    await page.goto("/shipper/tracking");
    await expect(page).toHaveURL(/\/login/);

    const status = await page.evaluate(async () => {
      const response = await fetch("/api/v1/shipper/tracking");
      return response.status;
    });
    expect(status).toBe(401);
  });
});
