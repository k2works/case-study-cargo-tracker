import { test, expect } from '@playwright/test';

// IT2 対応:
// - T-07: bookingId / shipperId 手入力フィールド廃止 (サーバ採番)
// - T-09: 荷主登録に name が必須
// - 検索 UI: BookingFormView の shipperId 入力は htmx 検索キーワード
//   (検索結果 list-group-item の onclick で shipperId に確定値が入る)
// - PRG: POST /bookings/new → 303 → /bookings/BK-XXXXXX

test.describe('US04 貨物予約登録 → PRG 遷移 (IT2)', () => {
  test('荷主登録 → 検索 UI で荷主確定 → 貨物予約 → 詳細画面に PRG', async ({ page }) => {
    const email = `booking.${Date.now()}@example.com`;
    const shipperName = 'E2E 用 荷主';

    // 1. 荷主登録 (自動採番)
    await page.goto('/shippers/new');
    await page.locator('#name').fill(shipperName);
    await page.locator('#email').fill(email);
    await page.locator('#address').fill('4-2-8 Shibakoen, Minato-ku, Tokyo');
    await page.locator('#kind-individual').check();
    await page.locator('button[type="submit"]').click();

    // 詳細ページ URL から SHP-XXXXXX を抽出
    await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
    const shipperUrl = page.url();
    const shipperId = shipperUrl.split('/').pop()!;

    // 2. 貨物予約フォーム (検索 UI で荷主を確定)
    await page.goto('/bookings/new');
    await expect(page.locator('h1')).toContainText('貨物予約');

    // htmx hx-trigger="keyup" を発火するため type() を使う。
    // 検索クエリは shipperId 接頭辞 (SHP-) を含めて確実にヒットさせる。
    await page.locator('#shipperId').click();
    await page.keyboard.type(shipperId.slice(0, 8));
    // htmx 結果が出るまで待つ (delay:300ms + ネットワーク)
    const candidate = page.locator(`#shipper-results .list-group-item-action`).first();
    await expect(candidate).toBeVisible({ timeout: 5000 });
    await candidate.click();
    await expect(page.locator('#shipperId')).toHaveValue(shipperId);

    // 港・期限入力
    await page.locator('#origin').selectOption('JPTYO');
    await page.locator('#destination').selectOption('USNYC');
    await page.locator('#deadline').fill('2026-08-31T18:00');
    await page.locator('button[type="submit"]').click();

    // PRG: 詳細ページ URL は /bookings/BK-XXXXXX
    await expect(page).toHaveURL(/\/bookings\/BK-[A-Z0-9]{6}$/);
    await expect(page.locator('h1')).toContainText('予約詳細');
    // IT2 ギャップ: 登録直後の予約は Draft 状態 (IT3 で submit 遷移 UI を追加予定)。
    await expect(page.locator('body')).toContainText('Draft');
  });
});

// IT2 ギャップ: 登録直後の Cargo は Draft 状態、引き渡しボタンは Submitted のみ
// 表示。Draft → Submitted の UI 遷移は IT2 未実装 (IT3 で SubmitBooking ハンドラを
// 追加予定)。本テストはハンドオーバ PRG の経路自体は実装済のため、ここでは
// `?error=invalid-state` 応答を検証する形に縮退する。
test.describe('US06 予約引き渡し (IT2 状態遷移 PRG)', () => {
  test('Draft 状態の予約は引き渡し不可で invalid-state エラーが返る', async ({ page }) => {
    const email = `handover.${Date.now()}@example.com`;

    // 荷主登録
    await page.goto('/shippers/new');
    await page.locator('#name').fill('引き渡し テスト 荷主');
    await page.locator('#email').fill(email);
    await page.locator('#address').fill('Tokyo');
    await page.locator('#kind-individual').check();
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/shippers\/SHP-/);
    const shipperId = page.url().split('/').pop()!;

    // 予約登録
    await page.goto('/bookings/new');
    await page.locator('#shipperId').fill(shipperId);
    await page.locator('#origin').selectOption('JPTYO');
    await page.locator('#destination').selectOption('USNYC');
    await page.locator('#deadline').fill('2026-09-01T10:00');
    await page.locator('button[type="submit"]').click();
    await expect(page).toHaveURL(/\/bookings\/BK-/);

    // Draft 状態なのでボタンは表示されない
    const handoverButton = page.locator('form[action$="/handover"] button[type="submit"]');
    await expect(handoverButton).toHaveCount(0);

    // 直接 /handover を POST すると invalid-state エラーで弾かれる
    const bookingId = page.url().split('/').pop()!;
    const response = await page.request.post(`/bookings/${bookingId}/handover`, {
      maxRedirects: 0,
    });
    expect(response.status()).toBe(303);
    const location = response.headers()['location'];
    expect(location).toMatch(/\?error=invalid-state&from=Draft/);
  });
});
