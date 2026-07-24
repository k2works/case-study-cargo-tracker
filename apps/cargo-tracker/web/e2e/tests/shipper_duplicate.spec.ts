import { test, expect } from '@playwright/test';

// US02 受入基準②: 同一メール登録時に既存荷主である旨を表示する。
test('同一メールで再登録するとエラーメッセージが表示される（US02 受入②）', async ({ page }) => {
  const email = `dup-${Date.now()}@example.com`;

  // 1 回目: 登録成功
  await page.goto('/shippers/new');
  await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
  await page.getByTestId('name').fill('重複テスト');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/shippers$/);

  // 2 回目: 同一メールでエラー表示
  await page.goto('/shippers/new');
  await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
  await page.getByTestId('name').fill('重複テスト2');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('submit').click();

  await expect(page.getByTestId('flash-error')).toContainText('既に荷主として登録されています');
});
