import { expect, type Page } from '@playwright/test';

/** シードが投入する固定の荷主 UUID（個人・田中太郎）。 */
export const SEED_SHIPPER_ID = '11111111-1111-1111-1111-111111111111';

/** 接続リセット等の一過性エラーに備えて goto をリトライする。 */
export async function gotoWithRetry(page: Page, url: string, tries = 4) {
  for (let i = 0; i < tries; i += 1) {
    try {
      await page.goto(url);
      return;
    } catch (e) {
      if (i === tries - 1) throw e;
      await page.waitForTimeout(500);
    }
  }
}

/** ログインしてダッシュボードまで遷移する。 */
export async function login(page: Page, username: string) {
  await gotoWithRetry(page, '/login');
  await page.fill('#username', username);
  await page.fill('#password', 'password');
  await page.getByTestId('login-submit').click();
  await page.waitForURL('http://localhost:8080/');
}

/** ナビバーの「ログアウト」を押して /login へ戻る。 */
export async function logout(page: Page) {
  await page.getByRole('button', { name: 'ログアウト' }).click();
  await page.waitForURL('**/login');
}

/** navbar「貨物予約」→ 一覧 → 指定予約の詳細、へメニュー経由で遷移する。 */
export async function navigateToBookingDetail(page: Page, bookingId: string) {
  await page.getByTestId('nav-bookings').click();
  await page.waitForURL('**/bookings');
  await expect(page.getByTestId('booking-table')).toBeVisible();
  await page.locator(`a[href="/bookings/${bookingId}"]`).click();
  await page.waitForURL(`**/bookings/${bookingId}`);
}

/** 予約詳細から経路設計画面へ遷移する（経路設計者）。 */
export async function navigateToRouteDesign(page: Page, bookingId: string) {
  await page.getByTestId('design-route-link').click();
  await page.waitForURL(`**/bookings/${bookingId}/route`);
}

/** 予約詳細の状態コード（data-status）を返す。 */
export async function bookingStatus(page: Page): Promise<string> {
  return (await page.getByTestId('booking-status').getAttribute('data-status')) ?? '';
}

/**
 * 予約を新規登録し、採番された予約番号を返す（デモのセットアップ用）。
 * navbar「貨物予約」→ ＋新規予約 フォーム経由で登録する（営業担当者）。
 */
export async function createBooking(
  page: Page,
  opts: { origin: string; destination: string; deadline: string; cargoType?: string },
): Promise<string> {
  await page.getByTestId('nav-bookings').click();
  await page.waitForURL('**/bookings');
  await page.getByTestId('booking-new-link').click();
  await page.waitForURL('**/bookings/new');

  await page.fill('#shipper_id', SEED_SHIPPER_ID);
  await page.fill('#consignee_name', 'LA Trading');
  await page.fill('#consignee_contact', 'consignee@example.com');
  await page.fill('#origin', opts.origin);
  await page.fill('#destination', opts.destination);
  await page.fill('#arrival_deadline', opts.deadline);
  await page.selectOption('#cargo_type', opts.cargoType ?? 'GENERAL');
  await page.fill('#weight', '1200');
  await page.getByTestId('booking-submit').click();

  await page.waitForURL(/\/bookings\/BKG-[^/]+$/);
  const url = page.url();
  return url.substring(url.lastIndexOf('/') + 1);
}
