import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers';

// US-ADM-01: 管理者による割引ポリシーの登録・一覧・編集（CRUD）を担保する。
test.describe('割引ポリシー管理（管理者）', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, USERS.admin);
  });

  test('割引ポリシーを登録し一覧・編集できる', async ({ page }) => {
    await page.goto('/admin/discount-policies');
    await expect(page.getByTestId('page-title')).toHaveText('割引ポリシー一覧');

    // 登録
    await page.getByTestId('new-policy').click();
    await expect(page).toHaveURL(/\/admin\/discount-policies\/new$/);
    await page.getByTestId('policy-type').selectOption('SEASONAL');
    await page.getByTestId('rate-percent').fill('5');
    await page.getByTestId('valid-from').fill('2026-03-01');
    await page.getByTestId('valid-until').fill('2026-06-30');
    await page.getByTestId('description').fill('春季キャンペーン');
    await page.getByTestId('submit').click();

    // PRG で一覧へ戻り、登録内容が表示される
    await expect(page).toHaveURL(/\/admin\/discount-policies$/);
    await expect(page.getByTestId('policy-table')).toContainText('シーズン割引');
    await expect(page.getByTestId('policy-table')).toContainText('5.0%');

    // 編集
    await page.getByTestId('edit-policy').first().click();
    await expect(page.getByTestId('page-title')).toHaveText('割引ポリシー編集');
    await page.getByTestId('rate-percent').fill('10');
    await page.getByTestId('submit').click();
    await expect(page).toHaveURL(/\/admin\/discount-policies$/);
    await expect(page.getByTestId('policy-table')).toContainText('10.0%');
  });

  test('割引率 30% 超はフォーム制約で送信できない', async ({ page }) => {
    await page.goto('/admin/discount-policies/new');
    await page.getByTestId('policy-type').selectOption('CORPORATE_STANDARD');
    await page.getByTestId('rate-percent').fill('50');
    await page.getByTestId('valid-from').fill('2026-01-01');
    await page.getByTestId('submit').click();
    // input[max=30] の制約により送信がブロックされフォームに留まる
    await expect(page).toHaveURL(/\/admin\/discount-policies\/new$/);
  });
});
