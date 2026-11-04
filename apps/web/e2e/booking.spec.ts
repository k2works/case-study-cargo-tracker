import { expect, test } from '@playwright/test';
import { businessDate } from './support/dates';

// 営業担当者のホーム（作業入口）から始める。識別子を握って API を叩かない（F8）。
test('US04 AC-01・AC-02 営業担当者が荷主を登録し、予約を登録すると、予約の一覧に見える', async ({ page }) => {
  const suffix = Date.now().toString();
  const shipperName = `E2E 荷主 ${suffix}`;

  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();

  // 荷主を登録
  await page.getByRole('navigation', { name: 'メインメニュー' }).getByRole('link', { name: '荷主' }).click();
  await page.getByRole('link', { name: '荷主を登録' }).click();
  await page.getByLabel('氏名または社名').fill(shipperName);
  await page.getByLabel('住所').fill('東京都千代田区 1-1');
  await page.getByLabel('連絡先').fill('03-0000-0000');
  await page.getByLabel('メールアドレス').fill(`e2e-${suffix}@example.com`);
  await page.getByRole('button', { name: /登録/ }).click();
  await expect(page.getByRole('heading', { name: '荷主の詳細' })).toBeVisible();
  await expect(page.getByText(shipperName)).toBeVisible();

  // 荷主の詳細から、その荷主で予約を登録（荷主を選び直さない）
  await page.getByRole('link', { name: 'この荷主で予約を登録' }).click();
  await expect(page.getByLabel('荷主')).toHaveValue(/^[0-9A-Z]{26}$/);
  await page.getByLabel('品名').fill('精密機器');
  await page.getByLabel('重量（kg）').fill('120');
  await page.getByLabel('長さ（cm）').fill('100');
  await page.getByLabel('幅（cm）').fill('80');
  await page.getByLabel('高さ（cm）').fill('60');
  await page.getByLabel('個数').fill('2');
  await page.getByLabel('出発地（UN/LOCODE）').fill('JPTYO');
  await page.getByLabel('目的地（UN/LOCODE）').fill('NLRTM');
  await page.getByLabel('希望引渡日').fill(businessDate(7));
  await page.getByLabel('希望期限').fill(businessDate(30));
  await page.getByRole('button', { name: '予約を登録' }).click();

  // 受け付けた結果が先に見え、一覧に反映される
  const accepted = page.getByText(/受け付けました（予約番号 (BK-\d{6})）/);
  await expect(accepted).toBeVisible();
  const number = /BK-\d{6}/.exec((await accepted.textContent()) ?? '')?.[0];
  expect(number).toBeTruthy();

  await page.getByRole('navigation', { name: 'メインメニュー' }).getByRole('link', { name: '予約' }).click();
  await expect(page.getByRole('link', { name: number! })).toBeVisible({ timeout: 20_000 });
});
