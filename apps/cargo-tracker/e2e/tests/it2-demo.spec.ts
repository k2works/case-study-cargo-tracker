import { test, expect, type Page } from '@playwright/test';
import { login } from './helpers';

/**
 * IT2 デモ項目（航海スケジュール）の E2E テスト。経路設計者がナビゲーション経由で操作する。
 *
 * 1. 航路一覧から航海スケジュールを新規登録する（US24）
 * 2. 登録済み航海を呼び出して更新する（US25）
 * 3. 出発地・目的地・貨物種別で検索する（US07）
 *
 * IT3 の経路候補（JPOSA→USLAX）に干渉しないよう、別レーン（JPTYO→USNYC）の航海を用いる。
 */

// このデモで登録・更新・検索する航海番号。
const VOYAGE = 'V9001';

/** navbar「航路管理」→ 航路一覧へ遷移する。 */
async function gotoVoyageList(page: Page) {
  await page.getByTestId('nav-voyages').click();
  await page.waitForURL('**/voyages');
  await expect(page.getByTestId('voyage-list-title')).toBeVisible();
}

test.describe.serial('IT2 デモ: 航海スケジュール', () => {
  test('US24: 航路一覧から航海スケジュールを新規登録する', async ({ page }) => {
    await login(page, 'designer');
    await gotoVoyageList(page);

    await page.getByTestId('voyage-new-link').click();
    await page.waitForURL('**/voyages/new');
    await page.fill('#voyage_number', VOYAGE);
    await page.fill('#vessel_name', 'DEMO MARU');
    await page.fill('#carrier', 'Demo Line');
    await page.check('#cargo_general');
    await page.fill('input[name="leg1_departure"]', 'JPTYO');
    await page.fill('input[name="leg1_arrival"]', 'USNYC');
    await page.fill('input[name="leg1_departure_time"]', '2026-06-01T18:00');
    await page.fill('input[name="leg1_arrival_time"]', '2026-06-20T08:00');
    await page.getByTestId('voyage-submit').click();

    // 登録後は航路一覧に戻り（flash クエリ付き）、登録した航海が表示される。
    await page.waitForURL(/\/voyages(\?|$)/);
    await expect(page.getByTestId('voyage-table')).toContainText(VOYAGE);
    await expect(page.getByTestId('voyage-table')).toContainText('DEMO MARU');
  });

  test('US25: 登録済み航海を呼び出して更新する', async ({ page }) => {
    await login(page, 'designer');
    await gotoVoyageList(page);

    await page.locator(`a[href="/voyages/${VOYAGE}/edit"]`).click();
    await page.waitForURL(`**/voyages/${VOYAGE}/edit`);
    // 現在の登録内容カードが差分確認用に表示される。
    await expect(page.getByTestId('voyage-current')).toBeVisible();

    // 船名を変更して更新する。
    await page.fill('#vessel_name', 'DEMO MARU II');
    await page.getByTestId('voyage-update-submit').click();

    await page.waitForURL(/\/voyages(\?|$)/);
    await expect(page.getByTestId('voyage-table')).toContainText('DEMO MARU II');
  });

  test('US07: 出発地・目的地・貨物種別で航海を検索する', async ({ page }) => {
    await login(page, 'designer');
    await gotoVoyageList(page);

    // JPTYO→USNYC・一般貨物で検索すると登録した航海が絞り込まれる。
    await page.fill('input[name="origin"]', 'JPTYO');
    await page.fill('input[name="destination"]', 'USNYC');
    await page.selectOption('select[name="cargo_type"]', 'GENERAL');
    await page.getByTestId('voyage-search-button').click();

    await page.waitForURL(/\/voyages\?/);
    await expect(page.getByTestId('voyage-table')).toContainText(VOYAGE);

    // 危険物で検索すると一般貨物専用の当該航海は該当しない。
    await page.fill('input[name="origin"]', 'JPTYO');
    await page.fill('input[name="destination"]', 'USNYC');
    await page.selectOption('select[name="cargo_type"]', 'HAZARDOUS');
    await page.getByTestId('voyage-search-button').click();
    await page.waitForURL(/\/voyages\?/);
    await expect(page.getByTestId('voyage-empty')).toBeVisible();
  });
});
