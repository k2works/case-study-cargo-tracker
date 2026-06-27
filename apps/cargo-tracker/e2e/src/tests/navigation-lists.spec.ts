import { test, expect } from '@playwright/test';

// IT2 ナビゲーション: 荷主一覧 / 貨物予約一覧 / 航海一覧
// すべてのページで navbar から到達可能。

test.describe('ナビゲーション一覧画面 (IT2)', () => {
  test('navbar から 3 つの一覧へ遷移できる', async ({ page }) => {
    await page.goto('/');

    // 荷主一覧
    await page.locator('nav a.nav-link', { hasText: '荷主一覧' }).click();
    await expect(page).toHaveURL(/\/shippers$/);
    await expect(page.locator('h1')).toContainText('荷主一覧');

    // 貨物予約一覧
    await page.locator('nav a.nav-link', { hasText: '貨物予約一覧' }).click();
    await expect(page).toHaveURL(/\/bookings$/);
    await expect(page.locator('h1')).toContainText('貨物予約一覧');

    // 航海一覧
    await page.locator('nav a.nav-link', { hasText: '航海一覧' }).click();
    await expect(page).toHaveURL(/\/voyages$/);
    await expect(page.locator('h1')).toContainText('航海一覧');
  });

  test('荷主一覧 → 詳細遷移できる', async ({ page }) => {
    // まず 1 件登録
    await page.goto('/shippers/new');
    const email = `nav.${Date.now()}@example.com`;
    await page.locator('#name').fill('一覧テスト 荷主');
    await page.locator('#email').fill(email);
    await page.locator('#address').fill('Tokyo');
    await page.locator('#kind-individual').check();
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/shippers\/SHP-/);

    // 一覧から「詳細」ボタンで遷移
    await page.goto('/shippers');
    const detailButton = page.locator('table tbody tr').filter({ hasText: email }).locator('a', { hasText: '詳細' });
    await expect(detailButton).toBeVisible();
    await detailButton.click();
    await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
    await expect(page.locator('body')).toContainText(email);
  });

  test('貨物予約一覧で予約 ID + 状態 + 貨物種別が表示される', async ({ page }) => {
    await page.goto('/bookings');
    // 既存予約がある (E2E 実行履歴により) — テーブルヘッダの存在を確認
    await expect(page.locator('table thead')).toContainText('予約 ID');
    await expect(page.locator('table thead')).toContainText('状態');
    await expect(page.locator('table thead')).toContainText('貨物種別');
  });

  test('航海一覧で航海番号 + 区間数 + バージョンが表示される', async ({ page }) => {
    await page.goto('/voyages');
    await expect(page.locator('table thead')).toContainText('航海番号');
    await expect(page.locator('table thead')).toContainText('区間数');
    await expect(page.locator('table thead')).toContainText('バージョン');
  });
});
