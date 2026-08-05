import { expect, test } from '@playwright/test';

/**
 * シナリオ③: 荷役登録 → 追跡ステータス反映（[テスト戦略](../docs/design/test_strategy.md) 3.4）。
 *
 * 荷役作業員が現場の作業を記録し、それが**荷主の追跡画面にそのまま出る**ところまでを
 * ブラウザで通す。IT10 では記録が荷主のタイムラインに出ておらず、
 * 「機能は入っているのに業務が回らない」状態だった（IT10 レビュー H8）。
 *
 * **HTTP 統合テストと重ねない**。判定の組み合わせ（経路逸脱・警告）は
 * `HandlingActivityTest` が表駆動で尽くしている。ここで見るのは
 * **ブラウザを介したときだけ壊れる**ところである。
 *
 * - 種別の選択が実際に送られているか（`select` の `name`）
 * - 港がマスタから選べるか（IT10 は手入力で、マスタ外のコードが 500 になった）
 * - PRG 後の通知が 1 回だけ出るか
 * - **未認証の公開追跡が、認証つきの画面を足した後も開けるか**
 *
 * 開発用シードが投入する `TRK-20260803-0001`（`BK-0001`・輸送中）を使う。
 * 予約から作り直さないのは、そこは既にシナリオ①が通しているためである。
 */

const HANDLER_USER = 'sato';
const TRACKER_USER = 'watanabe';
const TRACKER_PASSWORD = 'password';
const HANDLER_PASSWORD = 'password';

/** 開発用シードが必ず入れる貨物（`SharedDbDevSeed`） */
const SEEDED_TRACKING_NUMBER = 'TRK-20260803-0001';

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL(/\/$/);
}

test.describe('荷役の記録が荷主の追跡に出る', () => {
  test('荷役作業員がダッシュボードから登録し、公開追跡に反映される', async ({ page }) => {
    await login(page, HANDLER_USER, HANDLER_PASSWORD);

    // **ダッシュボードの作業入口から辿る**。URL を直接叩くと、
    // 導線が切れていても気付けない。
    // 荷役管理は navbar とダッシュボードの両方にあるため**範囲を指定する**——
    // 指定しないと 2 件に一致して落ちる。ここで見たいのは
    // 「そのロールが毎朝最初に見る画面から仕事へ入れるか」である
    await page.locator('#work-entries').getByRole('link', { name: '荷役管理' }).click();
    await expect(page).toHaveURL(/\/handling$/);
    await page.getByRole('link', { name: '荷役を登録する' }).click();
    await expect(page).toHaveURL(/\/handling\/new$/);

    await page.fill('input[name="trackingNumber"]', SEEDED_TRACKING_NUMBER);
    await page.selectOption('select[name="handlingType"]', 'UNLOAD');
    // **港はマスタから選ぶ**（IT10 レビュー M17）。手入力だと
    // マスタ外のコードが外部キー違反で 500 になっていた
    await page.selectOption('select[name="location"]', 'USLAX');
    await page.fill('input[name="completionTime"]', '2026-08-01T10:00');
    await page.fill('input[name="voyageNumber"]', 'V-0002S');
    await page.getByRole('button', { name: '登録する' }).click();

    // PRG で一覧へ戻り、通知が出ること
    await expect(page).toHaveURL(/\/handling$/);
    await expect(page.locator('.alert')).toContainText('荷役を登録しました');

    // **再読み込みで通知が消えること**。消えないと二重登録を疑わせる
    await page.reload();
    await expect(page.locator('.alert')).toHaveCount(0);

    // 履歴に残り、担当者が記録されていること（IT10 レビュー M10）
    const rows = page.locator('#handling-history tbody tr');
    await expect(rows.first()).toContainText(SEEDED_TRACKING_NUMBER);
    await expect(rows.first()).toContainText('荷降し');
    await expect(rows.first()).toContainText(HANDLER_USER);

    // **荷主の画面に出ること**（IT10 レビュー H8）。
    // ログアウトしてから開く——公開追跡は未認証で見えなければならない
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await page.goto(`/public/tracking/${SEEDED_TRACKING_NUMBER}`);
    const timeline = page.locator('table');
    await expect(timeline).toContainText('荷降し（UNLOAD）');
    await expect(timeline).toContainText('2026-08-01 10:00');
    await expect(timeline).toContainText('USLAX');
  });

  test('追跡番号で絞り込むと、その貨物の記録だけが出る', async ({ page }) => {
    await login(page, HANDLER_USER, HANDLER_PASSWORD);
    await page.goto('/handling');

    await page.fill('input[name="trackingNumber"]', 'TRK-20260803-9999');
    await page.getByRole('button', { name: '絞り込む' }).click();

    // 該当が無いことと、**打ち間違いに気付ける形**（入力した番号が残る）
    await expect(page.locator('#handling-empty')).toContainText('TRK-20260803-9999');
    await expect(page.locator('input[name="trackingNumber"]'))
      .toHaveValue('TRK-20260803-9999');
  });

  test('追跡管理者が遅延を記録すると、荷主の画面に理由が出る', async ({ page }) => {
    // **通知は将来リリース**であり、荷主が自分で確認できることが唯一の伝達手段である
    await login(page, TRACKER_USER, TRACKER_PASSWORD);

    await page.locator('#work-entries').getByRole('link', { name: '貨物追跡' }).click();
    await expect(page).toHaveURL(/\/tracking$/);
    await page.fill('input[name="trackingNumber"]', SEEDED_TRACKING_NUMBER);
    await page.getByRole('button', { name: '追跡する' }).click();

    await page.selectOption('select[name="exceptionType"]', 'DELAY');
    await page.selectOption('#exceptionLocation', 'USLAX');
    await page.fill('#exceptionOccurredAt', '2026-08-05T09:00');
    await page.fill('textarea[name="description"]', 'E2E: 本船の遅延により到着が遅れます');
    await page.getByRole('button', { name: '例外を記録する' }).click();

    // 荷主の画面（未認証）に理由が出る
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await page.goto(`/public/tracking/${SEEDED_TRACKING_NUMBER}`);
    const notice = page.locator('#tracking-exception');
    await expect(notice).toContainText('遅延が発生しています');
    await expect(notice).toContainText('本船の遅延により到着が遅れます');
  });
});
