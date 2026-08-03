import { expect, test } from '@playwright/test';

/**
 * 営業担当者が荷主を登録し、その荷主で貨物を予約して経路設計へ引き渡すまで。
 *
 * **HTTP 統合テストと重ねない**。同じ経路は
 * `apps/cargo-tracker/test/integration/**` が既に縦断している。
 * ここで見るのは**ブラウザを介したときだけ壊れる**ところである。
 *
 * - フォームの実送信（`name` 属性が実際に送られているか）
 * - htmx による入力欄の差し替え（貨物種別に応じた追加欄）
 * - Cookie のブラウザ側の扱い（PRG 後の通知が 1 回だけ出るか）
 * - 導線が押せて、押した先が意図した状態になっているか
 */

const SALES_USER = 'yamada';
const SALES_PASSWORD = 'password';

/** 同じ DB を使い回すため、実行ごとに衝突しない値を作る */
function unique() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

/** 荷主登録フォームを送信する（ボタンの文言は登録画面と予約画面で同じ） */
async function registerShipper(page) {
  await page.getByRole('button', { name: '登録する' }).click();
}

async function login(page) {
  await page.goto('/login');
  await page.fill('input[name="username"]', SALES_USER);
  await page.fill('input[name="password"]', SALES_PASSWORD);
  // **ボタンは名前で指定する**。`button[type="submit"]` はナビバーの
  // ログアウトボタンにも一致し、最初の 1 つ（＝ログアウト）を押してしまう。
  // 画面のどこを押したのかが読めるという点でも、名前で書くほうがよい
  await page.getByRole('button', { name: 'ログイン' }).click();
  // ダッシュボードへ着地すること（ログイン画面に留まらない）
  await expect(page).toHaveURL(/\/$/);
}

test.describe('貨物予約の一連の操作', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('荷主を登録すると、発行された荷主コードが画面に出る', async ({ page }) => {
    const suffix = unique();
    await page.goto('/shippers/new');
    await page.fill('input[name="name"]', `E2E 荷主 ${suffix}`);
    await page.fill('input[name="email"]', `e2e-${suffix}@example.com`);
    await registerShipper(page);

    // PRG で一覧へ戻り、通知に荷主コードが出ること（US02-3）
    await expect(page).toHaveURL(/\/shippers$/);
    const notice = page.locator('.alert-success');
    await expect(notice).toContainText('荷主を登録しました');
    await expect(notice).toContainText(/SHP-[0-9A-Z]{8}/);

    // **再読み込みで消えること**。消えないと「登録できていないのでは」と迷わせる
    await page.reload();
    await expect(page.locator('.alert-success')).toHaveCount(0);
  });

  test('一覧から絞り込み、その荷主で予約できる', async ({ page }) => {
    const suffix = unique();
    const shipperName = `E2E 絞込 ${suffix}`;

    await page.goto('/shippers/new');
    await page.fill('input[name="name"]', shipperName);
    await page.fill('input[name="email"]', `e2e-filter-${suffix}@example.com`);
    await registerShipper(page);

    // 絞り込む（GET フォーム）
    await page.goto('/shippers');
    await page.fill('input[name="q"]', shipperName);
    await page.getByRole('button', { name: '絞り込む' }).click();
    const rows = page.locator('table tbody tr');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(shipperName);

    // **導線の着地先まで通す**。押せることではなく、押した先の状態を見る
    await rows.first().getByRole('link', { name: 'この荷主で予約する' }).click();
    await expect(page).toHaveURL(/\/bookings\/new\?shipperCode=SHP-/);
    const shipperCode = await page.inputValue('input[name="shipperCode"]');
    expect(shipperCode).toMatch(/^SHP-[0-9A-Z]{8}$/);

    // 予約を登録する
    await page.selectOption('select[name="origin"]', 'JPOSA');
    await page.selectOption('select[name="destination"]', 'USLAX');
    await page.fill('input[name="arrivalDeadline"]', '2026-12-31');
    await page.fill('input[name="weight"]', '1200');
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page).toHaveURL(/\/bookings$/);
    await expect(page.locator('.alert-success')).toContainText(/BK-[0-9A-Z]{8}/);
  });

  test('冷凍貨物を選ぶと温度の欄が現れ、条件つきで予約できる', async ({ page }) => {
    const suffix = unique();
    await page.goto('/shippers/new');
    await page.fill('input[name="name"]', `E2E 冷凍 ${suffix}`);
    await page.fill('input[name="email"]', `e2e-cold-${suffix}@example.com`);
    await registerShipper(page);
    await page.goto('/shippers');
    await page.fill('input[name="q"]', `e2e-cold-${suffix}@example.com`);
    await page.getByRole('button', { name: '絞り込む' }).click();
    await page.locator('table tbody tr').first()
      .getByRole('link', { name: 'この荷主で予約する' }).click();

    // 初期状態では温度の欄がないこと
    await expect(page.locator('input[name="minTemperature"]')).toHaveCount(0);

    // **htmx による差し替え**。ここはブラウザを介さないと確かめられない
    await page.selectOption('select[name="cargoType"]', 'REFRIGERATED');
    await expect(page.locator('input[name="minTemperature"]')).toBeVisible();
    await expect(page.locator('input[name="maxTemperature"]')).toBeVisible();

    await page.selectOption('select[name="origin"]', 'JPOSA');
    await page.selectOption('select[name="destination"]', 'USLAX');
    await page.fill('input[name="arrivalDeadline"]', '2026-12-31');
    await page.fill('input[name="weight"]', '800');
    await page.fill('input[name="minTemperature"]', '-25.5');
    await page.fill('input[name="maxTemperature"]', '-18');
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page).toHaveURL(/\/bookings$/);
    const bookingId = (await page.locator('.alert-success').innerText())
      .match(/BK-[0-9A-Z]{8}/)[0];

    // 詳細で温度が**読める形**で出ること（内部表現をそのまま出さない）
    await page.goto(`/bookings/${bookingId}`);
    await expect(page.locator('table')).toContainText('-25.5');
    await expect(page.locator('table')).not.toContainText('-25500');
  });

  test('予約を経路設計へ引き渡すと状態が変わり、二度目は押せない', async ({ page }) => {
    const suffix = unique();
    await page.goto('/shippers/new');
    await page.fill('input[name="name"]', `E2E 引渡 ${suffix}`);
    await page.fill('input[name="email"]', `e2e-assign-${suffix}@example.com`);
    await registerShipper(page);
    await page.goto('/shippers');
    await page.fill('input[name="q"]', `e2e-assign-${suffix}@example.com`);
    await page.getByRole('button', { name: '絞り込む' }).click();
    await page.locator('table tbody tr').first()
      .getByRole('link', { name: 'この荷主で予約する' }).click();

    await page.selectOption('select[name="origin"]', 'JPOSA');
    await page.selectOption('select[name="destination"]', 'USLAX');
    await page.fill('input[name="arrivalDeadline"]', '2026-12-31');
    await page.fill('input[name="weight"]', '500');
    await page.getByRole('button', { name: '登録する' }).click();

    const bookingId = (await page.locator('.alert-success').innerText())
      .match(/BK-[0-9A-Z]{8}/)[0];

    await page.goto(`/bookings/${bookingId}`);
    await expect(page.locator('.badge')).toContainText('仮受付');
    await page.getByRole('button', { name: '経路設計へ引き渡す' }).click();

    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingId}$`));
    await expect(page.locator('.badge')).toContainText('経路設計中');

    // **押せなくなり、理由が示されること**。押せるのに失敗するボタンは
    // 利用者に「壊れている」と受け取られる
    await expect(page.getByRole('button', { name: '経路設計へ引き渡す' })).toHaveCount(0);
    await expect(page.locator('.alert-warning')).toContainText('引き渡せません');
  });

  test('危険物で申告を空のまま送ると、エラーが画面に出る', async ({ page }) => {
    const suffix = unique();
    await page.goto('/shippers/new');
    await page.fill('input[name="name"]', `E2E 危険物 ${suffix}`);
    await page.fill('input[name="email"]', `e2e-haz-${suffix}@example.com`);
    await registerShipper(page);
    await page.goto('/shippers');
    await page.fill('input[name="q"]', `e2e-haz-${suffix}@example.com`);
    await page.getByRole('button', { name: '絞り込む' }).click();
    await page.locator('table tbody tr').first()
      .getByRole('link', { name: 'この荷主で予約する' }).click();

    await page.selectOption('select[name="cargoType"]', 'HAZARDOUS');
    await expect(page.locator('input[name="unNumber"]')).toBeVisible();

    await page.selectOption('select[name="origin"]', 'JPOSA');
    await page.selectOption('select[name="destination"]', 'USLAX');
    await page.fill('input[name="arrivalDeadline"]', '2026-12-31');
    await page.fill('input[name="weight"]', '300');
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧へ進まず、**エラーが画面に出る**こと（IT4 は出ていなかった）
    await expect(page).toHaveURL(/\/bookings$/);
    await expect(page.locator('body')).toContainText('危険物申告');
  });
});
