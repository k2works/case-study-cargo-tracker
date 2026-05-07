import { test, expect } from '../fixtures';
import { BookingPage } from '../pages/BookingPage';

test.describe('貨物予約管理', () => {
  test('貨物予約一覧ページにアクセスできること', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.goto();
    await expect(bookingPage.heading).toBeVisible();
    await expect(bookingPage.newBookingLink).toBeVisible();
  });

  test('新規予約リンクから登録フォームに遷移できること', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);
    await bookingPage.goto();
    await bookingPage.newBookingLink.click();
    await expect(page).toHaveURL('/bookings/new');
    await expect(page.getByRole('heading', { name: '貨物予約 新規登録' })).toBeVisible();
  });

  test('貨物予約を新規登録できること', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);
    await page.goto('/bookings/new');
    await bookingPage.fillBookingForm('JPTYO', 'CNSHA');
    await bookingPage.submitForm();
    // 登録後に一覧ページに戻ること
    await expect(page).toHaveURL('/bookings');
  });

  test('登録した予約を一覧で確認できること', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);
    await page.goto('/bookings/new');
    await bookingPage.fillBookingForm('JPTYO', 'CNSHA');
    await bookingPage.submitForm();
    await expect(page).toHaveURL('/bookings');
    // 一覧にステータスバッジが1件以上表示されること
    await expect(page.getByText('仮予約').first()).toBeVisible();
  });

  test('経路設計ページにアクセスできること', async ({ page, loggedIn }) => {
    await page.goto('/routing/design');
    await expect(page.getByRole('heading', { name: '経路設計' })).toBeVisible();
    await expect(page.getByRole('button', { name: '経路を検索' })).toBeVisible();
  });
});
