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
    // bookingId なしで直接アクセスすると経路設計担当一覧にリダイレクトされる
    await page.goto('/routing/design');
    await expect(page).toHaveURL('/routing/assignments');
  });

  test('予約→経路割り当て→予約確定の一連フローが動作すること', async ({ page, loggedIn }) => {
    const bookingPage = new BookingPage(page);

    // 1. 新規予約を登録する（API レスポンスから bookingId を取得する）
    const [response] = await Promise.all([
      page.waitForResponse((res) =>
        res.url().includes('/api/booking/v1/cargos') && res.request().method() === 'POST'
      ),
      (async () => {
        await page.goto('/bookings/new');
        await bookingPage.fillBookingForm('JPTYO', 'CNSHA');
        await bookingPage.submitForm();
      })(),
    ]);
    const body = await response.json();
    const bookingId = body.bookingId;

    // 2. 作成した予約の詳細ページへ直接遷移する
    await page.goto(`/bookings/${bookingId}`);
    await expect(page.getByRole('heading', { name: /予約詳細/ })).toBeVisible();
    await expect(page.getByText('仮予約')).toBeVisible();

    // 3. 経路割り当てリンクをクリックして経路設計画面へ遷移する
    await page.getByRole('link', { name: '経路を割り当て →' }).click();
    await expect(page.getByRole('heading', { name: /経路設計/ })).toBeVisible();

    // 4. 経路を検索・選択・割り当てる
    await bookingPage.searchAndAssignRoute('JPTYO', 'CNSHA');

    // 5. 予約詳細ページに戻り「経路提案済み」に変わっていることを確認
    await expect(page.getByText('経路提案済み')).toBeVisible({ timeout: 10000 });

    // 6. 「予約を確定する」ボタンをクリックする
    await page.getByRole('button', { name: '予約を確定する' }).click();

    // 7. ステータスが「確定」に変わることを確認
    await expect(page.getByText('確定')).toBeVisible();
  });
});
