import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';
import { EstimatePage } from '../pages/EstimatePage';
import { BookingPage } from '../pages/BookingPage';

/** IT1 機能の E2E ハッピーパス。
 *  test_strategy.md の優先シナリオ（US13 / US15 / US18）は IT4 / IT5 で実装するため、
 *  IT1 時点では US26 認証・US02・US03・US01・US04 の登録系フローを通すことで縦串疎通を確認する。
 *  荷主コードはサーバー側で自動採番されるため、登録直後に一覧から最新コードを取得して後続テストで利用する。
 */

const uniqueSuffix = (): string =>
  `${Date.now().toString().slice(-6)}-${Math.floor(Math.random() * 10000)}`;

const futureDate = (daysAhead: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  return d.toISOString().slice(0, 10);
};

test.describe('US02 個人荷主登録', () => {
  test('個人荷主を登録すると一覧で確認できる', async ({ page, loggedIn }) => {
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    const email = `ind-${uniqueSuffix()}@example.com`;
    await shipper.fillForm({
      name: '山田太郎',
      email,
      phone: '03-1234-5678',
      address: '東京都千代田区',
      shipperType: 'Individual',
    });
    await shipper.submit();
    await shipper.gotoList();
    await expect(page.locator('body')).toContainText(email);
  });
});

test.describe('US03 法人荷主登録', () => {
  test('契約番号と割引率を伴う法人荷主を登録できる', async ({ page, loggedIn }) => {
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    const email = `corp-${uniqueSuffix()}@example.com`;
    await shipper.fillForm({
      name: '株式会社サンプル',
      email,
      phone: '03-9876-5432',
      address: '東京都港区',
      shipperType: 'Corporate',
      contractNumber: `CT-2026-${uniqueSuffix()}`,
      discountRate: 0.1,
    });
    await shipper.submit();
    await shipper.gotoList();
    await expect(page.locator('body')).toContainText(email);
  });
});

test.describe('US01 輸送見積', () => {
  test('一般貨物の見積を作成すると見積詳細でルート候補が表示される', async ({ page, loggedIn }) => {
    const estimate = new EstimatePage(page);
    await estimate.gotoNew();
    await estimate.fillForm({
      origin: 'JPTYO',
      destination: 'USLAX',
      deadline: futureDate(45),
      cargoType: 'General',
      weightKg: 1500,
    });
    await estimate.submit();
    // PRG で /estimates/:id へ
    await expect(page).toHaveURL(/\/estimates\/[^/]+$/);
    await expect(page.locator('body')).toContainText('JPTYO');
    await expect(page.locator('body')).toContainText('USLAX');
  });
});

test.describe('US04 貨物予約', () => {
  test('荷主登録 → 採番された荷主コードで貨物予約を登録できる', async ({ page, loggedIn }) => {
    // Given: 荷主を新規登録
    const shipper = new ShipperPage(page);
    await shipper.gotoNew();
    const email = `bk-${uniqueSuffix()}@example.com`;
    await shipper.fillForm({
      name: 'E2E テスト荷主',
      email,
      phone: '03-0000-0000',
      address: '東京都',
      shipperType: 'Individual',
    });
    await shipper.submit();

    // 自動採番された荷主コードを一覧の該当行から取得
    await shipper.gotoList();
    const row = page.locator('tr', { hasText: email });
    await expect(row).toBeVisible();
    const rowText = (await row.textContent()) ?? '';
    const codeMatch = rowText.match(/SH-\S+/);
    expect(codeMatch, '採番された SH- 形式の荷主コードが取得できる').not.toBeNull();
    const shipperCode = codeMatch![0];

    // When: 採番された荷主コードで予約を登録
    const booking = new BookingPage(page);
    await booking.gotoNew();
    await booking.fillForm({
      shipperCode,
      origin: 'JPYOK',
      destination: 'CNSHA',
      arrivalDeadline: futureDate(30),
      cargoType: 'General',
      weightKg: 800,
      description: 'E2E 一般貨物',
      quantity: 1,
    });
    await booking.submit();

    // Then: 予約詳細画面に遷移し、登録内容が表示される
    await expect(page).toHaveURL(/\/bookings\/[^/]+$/);
    await expect(page.locator('body')).toContainText('JPYOK');
    await expect(page.locator('body')).toContainText('CNSHA');
    await expect(page.locator('body')).toContainText(shipperCode);
  });
});
