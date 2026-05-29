import { test, expect, Page } from '@playwright/test';

/**
 * IT5 5.1: 追跡管理（S16 / S17）と荷役作業（S20 / S21）の軽量 UI E2E。
 *
 * Kafka は不要。authms + trackingms + handlingms + gatewayms の起動を前提とする
 * （データなし／空状態でも動作する範囲のみを検証する）。
 *
 * シナリオ:
 *   - ナビゲーションから両画面に到達できる（ROLE_ADMIN）
 *   - 空状態のガイダンスが表示される
 *   - 荷役作業フォームの動的バリデーション（種別別の必須フィールド）
 *   - 追跡管理一覧の表示
 *
 * cross-service の状態伝搬（実データを伴うシナリオ）は cross-service.spec.ts に分離する
 * （CROSS_SERVICE_E2E=1 必要）。
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

test.describe('IT5 / S16・S17: 追跡管理 UI', () => {
  test('US17: ナビから追跡管理一覧へ遷移し空状態が表示される', async ({ page }) => {
    await loginAsAdmin(page);

    await page.getByRole('link', { name: '追跡管理' }).click();
    await expect(page).toHaveURL('/tracking');
    await expect(page.getByRole('heading', { name: '追跡管理一覧' })).toBeVisible();
    // 空状態 or 1 件以上どちらでも、テーブル表示は出る
    await expect(page.getByRole('table')).toBeVisible();
  });

  test('US17: 存在しない追跡番号への直接アクセスでもエラー表示で耐える', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/tracking/TRK-NOTEXIST00/manage');
    // 「読み込み中…」か エラー表示のいずれか（fetch 失敗時は error 表示）
    await expect(page.getByText(/読み込み中|追跡情報の取得に失敗しました/)).toBeVisible({
      timeout: 10_000,
    });
  });
});

test.describe('IT5 / S20・S21: 荷役作業 UI', () => {
  test('US15: ナビから荷役作業履歴へ遷移し空状態が表示される', async ({ page }) => {
    await loginAsAdmin(page);

    await page.getByRole('link', { name: '荷役作業' }).click();
    await expect(page).toHaveURL('/handling');
    await expect(page.getByRole('heading', { name: '荷役作業履歴' })).toBeVisible();
    await expect(page.getByRole('button', { name: '新規記録' })).toBeVisible();
  });

  test('US15: 新規記録ボタンで荷役作業記録フォームへ遷移する', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/handling');

    await page.getByRole('button', { name: '新規記録' }).click();
    await expect(page).toHaveURL('/handling/new');
    await expect(page.getByRole('heading', { name: '荷役作業記録' })).toBeVisible();
    await expect(page.getByLabel('追跡番号')).toBeVisible();
    await expect(page.getByLabel('作業種別')).toBeVisible();
  });

  test('US15: LOAD 選択で「LOAD / UNLOAD では必須」ヒントが表示される', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/handling/new');

    await page.getByLabel('作業種別').selectOption('LOAD');
    await expect(page.getByText('LOAD / UNLOAD では必須')).toBeVisible();
  });

  test('US16: CLAIM 選択で荷受人確認フィールドが動的に表示される', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/handling/new');

    // 既定は RECEIVE。荷受人確認フィールドは非表示。
    await expect(page.getByText('荷受人確認（引取必須）')).not.toBeVisible();

    await page.getByLabel('作業種別').selectOption('CLAIM');
    await expect(page.getByText('荷受人確認（引取必須）')).toBeVisible();
    await expect(page.getByLabel(/荷受人氏名/)).toBeVisible();
    await expect(page.getByLabel('署名参照（URL / ID）')).toBeVisible();
    await expect(page.getByLabel('確認コード')).toBeVisible();
  });

  test('US16: CLAIM で署名・確認コードを空のまま送信するとクライアントバリデーションで拒否', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/handling/new');

    await page.getByLabel('追跡番号').fill('TRK-DUMMY01234');
    await page.getByLabel('作業場所（UN/LOCODE）').fill('USNYC');
    await page.getByLabel('作業員 ID').fill('H-E2E');
    await page.getByLabel('作業種別').selectOption('CLAIM');
    await page.getByLabel(/荷受人氏名/).fill('山田太郎');
    // 署名参照・確認コードは空のまま送信

    await page.getByRole('button', { name: '登録' }).click();

    await expect(page.getByRole('alert')).toHaveText(
      /引取作業では署名参照または確認コードのいずれかが必須です/,
    );
  });
});

test.describe('IT5 / ナビゲーション: 追跡管理 / 荷役作業の到達性', () => {
  test('ROLE_ADMIN で 8 件のメインナビが表示される（追跡管理 / 荷役作業を含む）', async ({ page }) => {
    await loginAsAdmin(page);

    await expect(page.getByRole('link', { name: 'ダッシュボード' })).toBeVisible();
    await expect(page.getByRole('link', { name: '荷主管理' })).toBeVisible();
    await expect(page.getByRole('link', { name: '見積管理' })).toBeVisible();
    await expect(page.getByRole('link', { name: '予約管理' })).toBeVisible();
    await expect(page.getByRole('link', { name: '航海スケジュール' })).toBeVisible();
    await expect(page.getByRole('link', { name: '経路設計' })).toBeVisible();
    await expect(page.getByRole('link', { name: '追跡管理' })).toBeVisible();
    await expect(page.getByRole('link', { name: '荷役作業' })).toBeVisible();
  });
});
