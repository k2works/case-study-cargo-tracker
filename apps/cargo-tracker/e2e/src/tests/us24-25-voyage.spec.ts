import { test, expect } from '../fixtures';
import { VoyagePage } from '../pages/VoyagePage';

/** US24 / US25: 航海スケジュール登録・更新の E2E。
 *  admin は MasterAdmin ロールを持つためナビバーに「航路管理」が表示される。
 */

const uniqueVoyageNumber = (): string =>
  `VY-${Date.now().toString().slice(-6)}`;

test.describe('US24 航海スケジュール新規登録', () => {
  test('航海を登録すると一覧に表示される', async ({ page, loggedIn }) => {
    const voyage = new VoyagePage(page);
    await voyage.gotoNew();

    const vn = uniqueVoyageNumber();
    await voyage.fillRegister({
      voyageNumber: vn,
      departureLocation: 'JPTYO',
      arrivalLocation: 'USLAX',
      departureTime: '2026-08-01T10:00',
      arrivalTime: '2026-08-15T18:00',
    });
    await voyage.submitRegister();

    await expect(page).toHaveURL('/voyages');
    await expect(page.locator('.alert-success')).toContainText(vn);
    await expect(page.locator('table')).toContainText(vn);
    await expect(page.locator('table')).toContainText('JPTYO');
    await expect(page.locator('table')).toContainText('USLAX');
  });

  test('同一航海番号の重複登録はエラーメッセージを返す', async ({ page, loggedIn }) => {
    const voyage = new VoyagePage(page);
    const vn = uniqueVoyageNumber();

    await voyage.gotoNew();
    await voyage.fillRegister({
      voyageNumber: vn,
      departureLocation: 'JPYOK',
      arrivalLocation: 'CNSHA',
      departureTime: '2026-08-01T08:00',
      arrivalTime: '2026-08-04T20:00',
    });
    await voyage.submitRegister();
    await expect(page).toHaveURL('/voyages');

    // 二度目の登録
    await voyage.gotoNew();
    await voyage.fillRegister({
      voyageNumber: vn,
      departureLocation: 'JPYOK',
      arrivalLocation: 'USNYC',
      departureTime: '2026-08-10T08:00',
      arrivalTime: '2026-09-01T20:00',
    });
    await voyage.submitRegister();

    await expect(page.locator('.alert-danger')).toContainText('既に登録されています');
  });
});

test.describe('US25 航海スケジュール更新', () => {
  test('登録 → 編集画面で既存値が表示され、更新できる', async ({ page, loggedIn }) => {
    const voyage = new VoyagePage(page);
    const vn = uniqueVoyageNumber();

    // 登録
    await voyage.gotoNew();
    await voyage.fillRegister({
      voyageNumber: vn,
      departureLocation: 'JPTYO',
      arrivalLocation: 'USLAX',
      departureTime: '2026-08-01T10:00',
      arrivalTime: '2026-08-15T18:00',
    });
    await voyage.submitRegister();
    await expect(page).toHaveURL('/voyages');

    // 編集画面: 既存値が fill されている
    await voyage.gotoEdit(vn);
    await expect(page.locator('input[name="voyageNumber"]')).toHaveValue(vn);
    await expect(page.locator('input[name="movements[0].departureLocation"]')).toHaveValue(
      'JPTYO',
    );

    // 区間を別ルートに置き換えて更新
    await page.locator('input[name="movements[0].departureLocation"]').fill('JPYOK');
    await page.locator('input[name="movements[0].arrivalLocation"]').fill('USNYC');
    await voyage.submitUpdate();

    await expect(page).toHaveURL('/voyages');
    await expect(page.locator('.alert-success')).toContainText(`${vn} を更新`);
    await expect(page.locator('table')).toContainText('JPYOK');
    await expect(page.locator('table')).toContainText('USNYC');
  });
});

test.describe('ナビバーのロール別表示', () => {
  test('admin（MasterAdmin）には「航路管理」リンクが表示される', async ({ page, loggedIn }) => {
    await page.goto('/');
    await expect(page.locator('nav a[href="/voyages"]')).toBeVisible();
  });
});
