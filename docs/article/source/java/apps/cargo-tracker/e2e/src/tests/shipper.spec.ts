import { test, expect } from '../fixtures';
import { ShipperIndexPage, ShipperNewPage, ShipperShowPage } from '../pages/ShipperPage';

test.describe('荷主管理', () => {
  test('荷主一覧ページが表示される', async ({ page, loggedIn }) => {
    const indexPage = new ShipperIndexPage(page);
    await indexPage.goto();

    await expect(page).toHaveURL('/shippers');
    await expect(indexPage.heading).toHaveText('荷主管理');
    await expect(indexPage.registerButton).toBeVisible();
    await expect(indexPage.table).toBeVisible();
  });

  test('個人荷主を登録できる', async ({ page, loggedIn }) => {
    const indexPage = new ShipperIndexPage(page);
    const newPage = new ShipperNewPage(page);
    const showPage = new ShipperShowPage(page);

    await indexPage.goto();
    await indexPage.clickRegister();

    await expect(page).toHaveURL('/shippers/new');
    await expect(newPage.heading).toHaveText('荷主登録');

    const timestamp = Date.now();
    const name = `テスト個人荷主 ${timestamp}`;
    const email = `individual-${timestamp}@example.com`;

    await newPage.fillIndividual(name, email, '090-1234-5678');
    await newPage.submit();

    // 登録後は詳細ページへリダイレクト
    await expect(page).toHaveURL(/\/shippers\/.+/);
    await expect(showPage.heading).toHaveText(name);
    await expect(showPage.getDetailValue('種別')).toHaveText('個人');
  });

  test('法人荷主を登録できる', async ({ page, loggedIn }) => {
    const newPage = new ShipperNewPage(page);
    const showPage = new ShipperShowPage(page);

    await newPage.goto();

    const timestamp = Date.now();
    const name = `テスト法人荷主 ${timestamp}`;
    const email = `corporate-${timestamp}@example.com`;

    await newPage.fillCorporate(name, email, '03-1234-5678', 'CN-TEST-001', '0.10');
    await newPage.submit();

    // 登録後は詳細ページへリダイレクト
    await expect(page).toHaveURL(/\/shippers\/.+/);
    await expect(showPage.heading).toHaveText(name);
    await expect(showPage.getDetailValue('種別')).toHaveText('法人');
    await expect(showPage.getDetailValue('契約番号')).toHaveText('CN-TEST-001');
  });

  test('荷主詳細ページが表示される', async ({ page, loggedIn }) => {
    const indexPage = new ShipperIndexPage(page);
    const newPage = new ShipperNewPage(page);
    const showPage = new ShipperShowPage(page);

    // 荷主を登録して詳細ページへ
    await newPage.goto();

    const timestamp = Date.now();
    const name = `詳細確認荷主 ${timestamp}`;
    const email = `detail-${timestamp}@example.com`;

    await newPage.fillIndividual(name, email, '080-9876-5432');
    await newPage.submit();

    // 詳細ページの内容を確認
    await expect(page).toHaveURL(/\/shippers\/.+/);
    await expect(showPage.heading).toHaveText(name);
    await expect(showPage.getDetailValue('メール')).toHaveText(email);
    await expect(showPage.getDetailValue('種別')).toHaveText('個人');
    await expect(showPage.backButton).toBeVisible();

    // 一覧へ戻る
    await showPage.backButton.click();
    await expect(page).toHaveURL('/shippers');

    // 一覧に登録した荷主が表示される
    const row = await indexPage.getShipperRowByName(name);
    await expect(row).toBeVisible();
  });

  test('荷主登録フォームから一覧へ戻れる', async ({ page, loggedIn }) => {
    const newPage = new ShipperNewPage(page);

    await newPage.goto();
    await newPage.backButton.click();

    await expect(page).toHaveURL('/shippers');
  });
});
