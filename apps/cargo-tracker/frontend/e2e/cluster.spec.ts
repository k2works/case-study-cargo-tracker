import { expect, test } from '@playwright/test';

/**
 * kind クラスタに対して実際に配ったものを踏む（IT2 の DoD）。
 *
 * <p>単体テストもモックの E2E も、「実際に配ったものが動くか」を判別しない。
 * イメージの作り忘れ・マイグレーションの失敗・サービス間の配線ミスは、
 * ここでしか出ない。</p>
 *
 * <p>`E2E_BASE_URL` が無いときは<b>読み込まない</b>（`playwright.config.ts` の
 * `testIgnore`）。skip にすると「飛ばした」のか「無い」のかが実行結果から
 * 読み取れず、0 件で緑の回が混じる。</p>
 */
test.describe('kind クラスタでの通し確認', () => {
  /** 業務タイムゾーンで作る。toISOString() は CI（UTC）で 1 日ずれる。 */
  function businessDate(offsetDays: number): string {
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    const at = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
    return formatter.format(at);
  }

  async function signIn(page: import('@playwright/test').Page, username: string) {
    await page.goto('/login');
    await page.getByLabel('利用者名').fill(username);
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('営業が荷主と貨物予約を登録し、一覧と詳細に出る', async ({ page }) => {
    const stamp = Date.now();
    const email = `cluster-${stamp}@example.com`;
    const product = `クラスタ確認-${stamp}`;

    await signIn(page, 'sales01');

    // 荷主を登録する。
    await page.goto('/shippers/new');
    await page.getByLabel('名称').fill(`クラスタ商事 ${stamp}`);
    await page.getByLabel('メールアドレス').fill(email);
    await page.getByLabel('電話番号').fill('03-0000-0000');
    await page.getByLabel('住所').fill('東京都中央区');
    await page.getByRole('button', { name: '登録する' }).click();

    // 投影は非同期なので、一覧に出るまで待つ。
    await expect(page.getByText(email)).toBeVisible({ timeout: 20_000 });

    await page.getByRole('link', { name: '予約登録' }).first().click();
    await expect(page.getByRole('heading', { name: '貨物予約の登録' })).toBeVisible();

    // 荷主は選ぶ。識別子を打たせると、営業は一覧を開いて UUID を書き写すことになる。
    // 選択肢は「名称（荷主コード）」なので、名称の部分で当てる。
    const option = page.locator('#shipperId option', { hasText: `クラスタ商事 ${stamp}` });
    await expect(option).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主').selectOption(await option.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('USNYC');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill(product);
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧に出る（予約番号が採番され、状態は仮受付）。
    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
    const row = page.locator('tr', { hasText: product });
    await expect(row.getByText('仮受付')).toBeVisible();

    // 詳細まで開く。一覧に出るだけでは、詳細の配線が通っているか分からない。
    await row.getByRole('link').first().click();
    await expect(page.getByRole('heading', { name: /^予約 B-/ })).toBeVisible();
    await expect(page.getByText(product)).toBeVisible();
    await expect(page.getByText('120 × 80 × 100 cm')).toBeVisible();
  });

  test('集約が断ると理由が出る（500 にならない）', async ({ page }) => {
    await signIn(page, 'sales01');
    await page.goto('/bookings/new');

    // 荷主は一覧の先頭を選ぶ。ここで見たいのは経路の拒否なので、誰でもよい。
    const first = page.locator('#shipperId option').nth(1);
    await expect(first).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主').selectOption(await first.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('JPTYO');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill('同一港');
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByRole('alert')).toContainText('出発地と目的地が同じ');
  });

  test('経路設計には引き渡し待ちの件数と導線が出る', async ({ page }) => {
    await signIn(page, 'routing01');

    // 上のテストで仮受付が 1 件以上ある。件数からその場で一覧へ行けること。
    const notice = page.getByText(/引き渡し待ちの予約が \d+ 件/);
    await expect(notice).toBeVisible({ timeout: 20_000 });
    await notice.getByRole('link', { name: '予約一覧' }).click();

    await expect(page.getByRole('heading', { name: '予約一覧' })).toBeVisible();
  });

  test('管理者は利用者の状態を見てロックを解除できる', async ({ page, request }) => {
    // 先に API で 5 回失敗させてロックする。画面から 5 回打つと、E2E が
    // 「ロックの再現手順」ではなく「入力の反復」を測ることになる。
    for (let i = 0; i < 5; i++) {
      await request.post('/api/v1/auth/login', {
        data: { username: 'handler01', password: 'wrong-password' },
        failOnStatusCode: false,
      });
    }

    await signIn(page, 'admin01');
    await page.goto('/admin/users');

    const row = page.locator('tr', { hasText: 'handler01' });
    await expect(row.getByText(/ロック中（あと \d+ 分）/)).toBeVisible();

    await row.getByRole('button', { name: '解除する' }).click();
    await expect(row.getByText(/ロック中/)).toHaveCount(0);

    // 解除できたことを、画面の見え方ではなく実際のログインで確かめる。
    const response = await request.post('/api/v1/auth/login', {
      data: { username: 'handler01', password: 'secret1234' },
      failOnStatusCode: false,
    });
    expect(response.status()).toBe(200);
  });

  test('未認証でもポータルから公開追跡へ入れる', async ({ page }) => {
    await page.goto('/portal');

    await page.getByLabel('追跡番号').fill('ABC12345');
    await page.getByRole('button', { name: '照会する' }).click();

    await expect(page.getByRole('heading', { name: '荷物の追跡' })).toBeVisible();
    await expect(page.getByText('ABC12345')).toBeVisible();
  });
});
