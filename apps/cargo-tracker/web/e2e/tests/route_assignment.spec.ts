import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers';

// IT4 デモ項目の受け入れ E2E（US08 経路候補算出・US09 経路選択/確定）。
// 経路探索は ROLE_ROUTE_DESIGNER、予約登録は ROLE_SALES。ロールを切り替えて検証する。
function unique(prefix: string): string {
  return `${prefix}${Date.now() % 100000}${Math.floor(Math.random() * 1000)}`;
}

// 実行日から相対の日付文字列（YYYY-MM-DD）。ハードコード日付による将来の CI 赤化を防ぐ。
function daysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

// designer で JPTYO→USLAX の直行便を登録する（経路候補が存在する状態を作る）。
async function registerDirectVoyage(page: Page): Promise<string> {
  const num = unique('VD');
  await page.goto('/voyages/new');
  await page.getByTestId('voyage-number').fill(num);
  await page.getByTestId('vessel-name').fill('Direct Liner');
  await page.getByTestId('carrier').fill('Evergreen');
  await page.getByTestId('dep-1').fill('JPTYO');
  await page.getByTestId('arr-1').fill('USLAX');
  await page.getByTestId('depdate-1').fill(daysFromNow(10));
  await page.getByTestId('arrdate-1').fill(daysFromNow(22));
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/voyages$/);
  return num;
}

// sales で荷主を登録し荷主コードを返す。
async function registerShipper(page: Page): Promise<string> {
  const email = `${unique('it4')}@example.com`;
  await page.goto('/shippers/new');
  await page.getByTestId('shipper-type').selectOption('INDIVIDUAL');
  await page.getByTestId('name').fill('IT4 荷主');
  await page.getByTestId('email').fill(email);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/shippers$/);
  const code = await page.getByTestId('shipper-row').first().locator('td').first().textContent();
  return code!.trim();
}

// sales で JPTYO→dest の予約を登録し経路設計者へ引き渡し、予約番号を返す。
async function createProposedBooking(page: Page, dest: string): Promise<string> {
  const code = await registerShipper(page);
  await page.goto('/bookings/new');
  await page.getByTestId('shipper-code').fill(code);
  await page.getByTestId('cargo-type').selectOption('GENERAL');
  await page.getByTestId('weight').fill('500');
  await page.getByTestId('origin').fill('JPTYO');
  await page.getByTestId('destination').fill(dest);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/bookings\/confirm\/BKG-/);
  await page.getByTestId('to-detail').click();
  await expect(page).toHaveURL(/\/bookings\/BKG-/);
  const bookingID = (await page.getByTestId('booking-id').textContent())!.trim();
  await page.getByTestId('assign-routing').click();
  await expect(page.getByTestId('booking-status')).toContainText('経路提案済み');
  return bookingID;
}

// sales で JPTYO→USLAX の予約を期限つきで登録し経路設計者へ引き渡す（US10 用）。
async function createProposedBookingWithDeadline(page: Page, deadline: string): Promise<string> {
  const code = await registerShipper(page);
  await page.goto('/bookings/new');
  await page.getByTestId('shipper-code').fill(code);
  await page.getByTestId('cargo-type').selectOption('GENERAL');
  await page.getByTestId('weight').fill('500');
  await page.getByTestId('origin').fill('JPTYO');
  await page.getByTestId('destination').fill('USLAX');
  await page.getByTestId('arrival-deadline').fill(deadline);
  await page.getByTestId('submit').click();
  await expect(page).toHaveURL(/\/bookings\/confirm\/BKG-/);
  await page.getByTestId('to-detail').click();
  const bookingID = (await page.getByTestId('booking-id').textContent())!.trim();
  await page.getByTestId('assign-routing').click();
  await expect(page.getByTestId('booking-status')).toContainText('経路提案済み');
  return bookingID;
}

test.describe('US10: 経路条件を調整して再算出する', () => {
  test('期限超過で候補ゼロ→期限延長で再算出→確定できる', async ({ page }) => {
    // 直行便は到着 daysFromNow(22)。期限を 15 日後にして候補ゼロにする。
    await login(page, USERS.designer);
    await registerDirectVoyage(page);

    await login(page, USERS.sales);
    const bookingID = await createProposedBookingWithDeadline(page, daysFromNow(15));

    await login(page, USERS.designer);
    await page.goto(`/bookings/${bookingID}/route`);
    // 現在の制約条件と該当なしが表示される
    await expect(page.getByTestId('route-conditions')).toBeVisible();
    await expect(page.getByTestId('route-none')).toBeVisible();

    // 期限を 30 日後に延長して再算出
    await page.getByTestId('override-deadline').fill(daysFromNow(30));
    await page.getByTestId('recalculate').click();
    await expect(page.getByTestId('deadline-adjusted')).toBeVisible();
    await expect(page.getByTestId('route-candidate').first()).toBeVisible();

    // 調整後の候補で確定 → ROUTED
    await page.getByTestId('assign-route').click();
    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingID}$`));
    await expect(page.getByTestId('routing-status')).toContainText('経路確定');
  });

  test('調整後も候補がなければ営業へ条件協議を依頼できる', async ({ page }) => {
    await login(page, USERS.designer);
    await registerDirectVoyage(page);

    await login(page, USERS.sales);
    const bookingID = await createProposedBookingWithDeadline(page, daysFromNow(15));

    await login(page, USERS.designer);
    await page.goto(`/bookings/${bookingID}/route`);
    await expect(page.getByTestId('request-negotiation')).toBeVisible();
    await page.getByTestId('request-negotiation').click();
    // PRG で予約詳細へ
    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingID}$`));
  });
});

test.describe('US08/US09: 経路候補算出・選択・確定', () => {
  test('引き渡し済み予約に経路候補を算出し確定できる（ROUTED）', async ({ page }) => {
    // 1) designer で直行便を用意
    await login(page, USERS.designer);
    await registerDirectVoyage(page);

    // 2) sales で予約を登録し経路設計者へ引き渡し
    await login(page, USERS.sales);
    const bookingID = await createProposedBooking(page, 'USLAX');

    // 3) designer で経路割り当て画面を開き候補を確認・確定
    await login(page, USERS.designer);
    await page.goto(`/bookings/${bookingID}/route`);
    await expect(page.getByTestId('page-title')).toHaveText('経路割り当て');
    await expect(page.getByTestId('route-candidate').first()).toBeVisible();
    await expect(page.getByTestId('candidate-kind').first()).toContainText('直行');
    await page.getByTestId('assign-route').click();

    // 4) 予約詳細に確定経路と経路状態 ROUTED が表示される
    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingID}$`));
    await expect(page.getByTestId('routing-status')).toContainText('経路確定');
    await expect(page.getByTestId('itinerary-leg').first()).toBeVisible();
  });

  test('該当する経路がない場合は通知される（US08 異常系）', async ({ page }) => {
    // 直行便は JPTYO→USLAX のみ。到達不能な目的地 BRSSZ で予約を作る。
    await login(page, USERS.designer);
    await registerDirectVoyage(page);

    await login(page, USERS.sales);
    const bookingID = await createProposedBooking(page, 'BRSSZ');

    await login(page, USERS.designer);
    await page.goto(`/bookings/${bookingID}/route`);
    await expect(page.getByTestId('route-none')).toBeVisible();
    await expect(page.getByTestId('route-none')).toContainText('到達可能な経路候補が見つかりません');
  });
});
