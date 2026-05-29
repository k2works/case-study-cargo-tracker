import { test, expect, Page } from '@playwright/test';

/**
 * IT6 4.1: US19 / US20 例外管理 UI の E2E（S18 / S19）。
 *
 * Kafka は不要。authms + trackingms + gatewayms の起動を前提とし、
 * 既存データに依存せず、空状態と動的バリデーションを検証する。
 *
 * 完全な「例外登録 → 通知 → cross-service」貫通シナリオは cross-service.spec.ts の
 * CROSS_SERVICE_E2E=1 経路に拡張する。本 spec は UI/UX の振る舞いを担保する。
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

test.describe('IT6 / S19: 例外対応一覧 UI', () => {
  test('US19/US20: ナビから例外対応一覧へ遷移できる', async ({ page }) => {
    await loginAsAdmin(page);

    await page.getByRole('link', { name: '例外対応' }).click();
    await expect(page).toHaveURL('/tracking/exceptions');
    await expect(page.getByRole('heading', { name: '例外対応一覧' })).toBeVisible();
    // フィルタが表示される
    await expect(page.getByLabel('対応状態')).toBeVisible();
  });

  test('US19: フィルタの選択肢が「全て / 未対応 / 対応中 / 解決済」である', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/exceptions');

    const filter = page.getByLabel('対応状態');
    await expect(filter).toBeVisible();
    await expect(filter.locator('option')).toHaveText(['全て', '未対応', '対応中', '解決済']);
  });

  test('US19: 空状態のメッセージが表示される（データなし時）', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/exceptions');

    // 例外が 1 件もない初期状態では「例外がありません」セルが、データがある環境では tracking 番号セルが表示される。
    // strict mode 違反を避けるため td セル（role=cell）レベルで限定して or を組む。
    const emptyCell = page.getByRole('cell', { name: '例外がありません' });
    const dataCell = page.locator('td').filter({ hasText: /^TRK-/ });
    await expect(emptyCell.or(dataCell.first())).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('IT6 / S18: 例外登録 UI', () => {
  test('US19/US20: /tracking/:tn/exceptions/new に直接アクセスして種別ラジオが表示される', async ({
    page,
  }) => {
    await loginAsAdmin(page);

    // 追跡番号は存在しなくても画面は描画される（API 呼び出しは送信時）
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');

    await expect(page.getByRole('heading', { name: /例外登録/ })).toBeVisible();
    await expect(page.getByRole('radio', { name: '遅延' })).toBeVisible();
    await expect(page.getByRole('radio', { name: '破損' })).toBeVisible();
    await expect(page.getByRole('radio', { name: '紛失' })).toBeVisible();
  });

  test('US20: 紛失（LOSS）を選択すると escalation 警告と確認チェックが表示される', async ({
    page,
  }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');

    // 初期は表示されていない
    await expect(page.getByText('⚠ 紛失を選択しました')).not.toBeVisible();

    // 紛失を選択
    await page.getByRole('radio', { name: '紛失' }).click();

    await expect(page.getByText('⚠ 紛失を選択しました')).toBeVisible();
    await expect(
      page.getByLabel(/escalation を承知のうえ登録する/),
    ).toBeVisible();
  });

  test('US20: LOSS で確認チェックを外したまま送信すると検証エラーが表示される', async ({
    page,
  }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');

    await page.getByRole('radio', { name: '紛失' }).click();
    await page.getByLabel('発生状況・理由').fill('貨物紛失を確認');
    await page.getByRole('button', { name: '例外を記録' }).click();

    await expect(page.getByText(/escalation 通知の確認が必要/)).toBeVisible({
      timeout: 5_000,
    });
  });

  test('US19/US20: キャンセルで追跡詳細へ戻る', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');

    await page.getByRole('button', { name: 'キャンセル' }).click();
    await expect(page).toHaveURL('/tracking/TRK-AB12CD3456/manage');
  });

  test('US19/US20: 紛失から遅延に切り替えると警告が消える', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/tracking/TRK-AB12CD3456/exceptions/new');

    await page.getByRole('radio', { name: '紛失' }).click();
    await expect(page.getByText('⚠ 紛失を選択しました')).toBeVisible();

    await page.getByRole('radio', { name: '遅延' }).click();
    await expect(page.getByText('⚠ 紛失を選択しました')).not.toBeVisible();
  });
});
