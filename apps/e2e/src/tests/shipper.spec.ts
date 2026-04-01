import { test, expect } from '../fixtures';
import { ShipperPage } from '../pages/ShipperPage';

test.describe('E02: 個人荷主登録', () => {
  test('氏名・連絡先・メールアドレスで個人荷主を登録できる', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);

    // 荷主登録フォームに遷移する
    await shipperPage.goto();

    // 個人ラジオボタンがデフォルトで選択されていることを確認する
    await expect(page.locator('input[name="category"][value="INDIVIDUAL"]')).toBeChecked();

    // 法人情報セクションが非表示であることを確認する
    await expect(page.locator('#corporateSection')).toBeHidden();

    // フォームを入力して送信する
    await shipperPage.registerIndividual(
      '山田 太郎',
      `yamada-${Date.now()}@example.com`,
      '03-1234-5678',
    );

    // 荷主一覧にリダイレクトされる
    await expect(page).toHaveURL('/shippers');

    // 登録成功メッセージが表示される
    await expect(page.locator('.alert-success')).toContainText('荷主を登録しました');
  });
});

test.describe('E03: 法人荷主登録', () => {
  test('法人種別を選択すると法人契約情報フィールドが表示される', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);
    await shipperPage.goto();

    // 初期状態では法人情報セクションが非表示
    await expect(page.locator('#corporateSection')).toBeHidden();

    // 法人ラジオボタンをクリックする
    await page.locator('input[name="category"][value="CORPORATE"]').check();

    // 法人情報セクションが表示される
    await expect(page.locator('#corporateSection')).toBeVisible();

    // 法人情報フィールドが存在することを確認する
    await expect(page.locator('input[name="contractNumber"]')).toBeVisible();
    await expect(page.locator('input[name="discountRate"]')).toBeVisible();
  });

  test('法人荷主を登録できる', async ({ page, loggedIn }) => {
    const shipperPage = new ShipperPage(page);

    // 法人荷主を登録する
    await shipperPage.registerCorporate(
      '株式会社テスト物流',
      `corp-${Date.now()}@example.com`,
      '03-9876-5432',
      'CONTRACT-001',
      '10',
    );

    // 荷主一覧にリダイレクトされる
    await expect(page).toHaveURL('/shippers');

    // 登録成功メッセージが表示される
    await expect(page.locator('.alert-success')).toContainText('荷主を登録しました');
  });
});
