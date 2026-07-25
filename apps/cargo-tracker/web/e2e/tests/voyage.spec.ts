import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers';

// IT3 デモ項目の受け入れ E2E（US24 航海登録・US25 更新・US07 検索）。
// 航路は ROLE_ROUTE_DESIGNER 権限。admin は全機能アクセス可のため admin で検証する。
function uniqueNumber(prefix: string): string {
  return `${prefix}${Date.now() % 100000}`;
}

async function registerVoyage(page: Page, number: string, refrigerated: boolean): Promise<void> {
  await page.goto('/voyages/new');
  await page.getByTestId('voyage-number').fill(number);
  await page.getByTestId('vessel-name').fill('Ever Given');
  await page.getByTestId('carrier').fill('Evergreen');
  if (refrigerated) await page.getByTestId('ct-refrigerated').check();
  await page.getByTestId('dep-1').fill('JPTYO');
  await page.getByTestId('arr-1').fill('SGSIN');
  await page.getByTestId('depdate-1').fill('2026-09-01');
  await page.getByTestId('arrdate-1').fill('2026-09-05');
  await page.getByTestId('dep-2').fill('SGSIN');
  await page.getByTestId('arr-2').fill('USLAX');
  await page.getByTestId('depdate-2').fill('2026-09-06');
  await page.getByTestId('arrdate-2').fill('2026-09-12');
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/voyages$/);
}

test.beforeEach(async ({ page }) => {
  // 経路設計者（ROLE_ROUTE_DESIGNER）で航路管理を操作する
  await login(page, USERS.designer);
});

test.describe('US24: 航海スケジュール登録', () => {
  test('ナビ「航路管理」から一覧へ到達できる', async ({ page }) => {
    await page.getByTestId('nav-voyages').click();
    await expect(page).toHaveURL(/\/voyages$/);
    await expect(page.getByTestId('page-title')).toHaveText('航路一覧');
  });

  test('航海スケジュールを登録し一覧に表示される', async ({ page }) => {
    const num = uniqueNumber('V');
    await registerVoyage(page, num, false);
    await expect(page.getByText(num).first()).toBeVisible();
  });

  test('同一航海番号の重複登録はエラー', async ({ page }) => {
    const num = uniqueNumber('V');
    await registerVoyage(page, num, false);
    await page.goto('/voyages/new');
    await page.getByTestId('voyage-number').fill(num);
    await page.getByTestId('vessel-name').fill('Dup');
    await page.getByTestId('carrier').fill('Dup');
    await page.getByTestId('dep-1').fill('JPTYO');
    await page.getByTestId('arr-1').fill('USLAX');
    await page.getByTestId('depdate-1').fill('2026-09-01');
    await page.getByTestId('arrdate-1').fill('2026-09-10');
    await page.getByTestId('submit').click();
    await expect(page.getByTestId('flash-error')).toContainText('既に登録');
  });
});

test.describe('US25: 航海スケジュール更新', () => {
  test('登録済み航海の船名を更新できる', async ({ page }) => {
    const num = uniqueNumber('V');
    await registerVoyage(page, num, false);
    await page.goto(`/voyages/${num}/edit`);
    await expect(page.getByTestId('cur-number')).toContainText(num);
    await page.getByTestId('vessel-name').fill('Ever Ace');
    await page.getByTestId('depdate-1').fill('2026-10-01');
    await page.getByTestId('arrdate-1').fill('2026-10-10');
    await page.getByTestId('update').click();
    await expect(page).toHaveURL(/\/voyages$/);
    await expect(page.getByText('Ever Ace').first()).toBeVisible();
  });
});

test.describe('US07: 航海スケジュール検索', () => {
  test('冷凍貨物で対応航海のみに絞り込める', async ({ page }) => {
    const reefer = uniqueNumber('R');
    await registerVoyage(page, reefer, true);
    await page.goto('/voyages/search');
    await page.getByTestId('cargo-type').selectOption('REFRIGERATED');
    await page.getByTestId('do-search').click();
    await expect(page.getByTestId('search-results')).toBeVisible();
    // 冷凍対応の航海が結果に含まれる
    await expect(page.getByText(reefer).first()).toBeVisible();
  });
});
