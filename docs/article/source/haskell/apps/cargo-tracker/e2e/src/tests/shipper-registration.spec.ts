import { test, expect } from '@playwright/test';

// IT2 対応:
// - T-07: shipperId はサーバ採番 (フォームから入力フィールド撤去)
// - T-09: name フィールド必須化
// - PRG: POST /shippers/new → 303 → GET /shippers/:id (詳細ページ)

test.describe('US02 個人荷主登録 (IT2 自動採番 + name)', () => {
  test('個人荷主を登録すると荷主詳細画面に PRG 遷移する', async ({ page }) => {
    await page.goto('/shippers/new');
    await expect(page.locator('h1')).toContainText('荷主');

    await page.locator('#name').fill('山田 太郎');
    await page.locator('#email').fill(`taro.${Date.now()}@example.com`);
    await page.locator('#address').fill('4-2-8 Shibakoen, Minato-ku, Tokyo');
    await page.locator('#kind-individual').check();
    await page.locator('button[type="submit"]').click();

    // PRG: 詳細ページの URL は /shippers/SHP-XXXXXX
    await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
    await expect(page.locator('h1')).toContainText('荷主詳細');
    await expect(page.locator('body')).toContainText('山田 太郎');
  });
});

test.describe('US03 法人荷主登録 (IT2 自動採番 + name)', () => {
  test('法人荷主を登録すると荷主詳細画面に PRG 遷移する', async ({ page }) => {
    await page.goto('/shippers/new');

    await page.locator('#name').fill('株式会社サンプル');
    await page.locator('#email').fill(`corp.${Date.now()}@example.com`);
    await page.locator('#address').fill('1-1-1 Marunouchi, Chiyoda-ku, Tokyo');
    await page.locator('#kind-corporate').check();
    await page.locator('#corporateNumber').fill('1234567890123');
    await page.locator('#contractRank').selectOption('Gold');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
    await expect(page.locator('body')).toContainText('株式会社サンプル');
    await expect(page.locator('body')).toContainText('法人');
  });
});

test.describe('T-08 フォーム ?error= フラッシュ表示', () => {
  test('?error=duplicate-email を付けて GET すると Bootstrap alert が出る', async ({ page }) => {
    await page.goto('/shippers/new?error=duplicate-email');
    await expect(page.locator('.alert-danger')).toBeVisible();
    await expect(page.locator('.alert-danger')).toContainText('メール');
  });
});
