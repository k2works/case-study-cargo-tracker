import { test, expect } from '../fixtures';
import { NavbarPage, DashboardPage } from '../pages/NavbarPage';

test.describe('共通ナビゲーション', () => {
  test.describe('ダッシュボード', () => {
    test('ダッシュボードが表示される', async ({ page, loggedIn }) => {
      const dashboard = new DashboardPage(page);
      await dashboard.goto();

      await expect(page).toHaveURL('/');
      await expect(dashboard.heading).toHaveText('ダッシュボード');
      await expect(dashboard.bookingsCard).toBeVisible();
      await expect(dashboard.shippersCard).toBeVisible();
    });

    test('予約管理カードから予約一覧へ遷移できる', async ({ page, loggedIn }) => {
      const dashboard = new DashboardPage(page);
      await dashboard.goto();
      await dashboard.bookingsCard.click();

      await expect(page).toHaveURL('/bookings');
    });

    test('荷主管理カードから荷主一覧へ遷移できる', async ({ page, loggedIn }) => {
      const dashboard = new DashboardPage(page);
      await dashboard.goto();
      await dashboard.shippersCard.click();

      await expect(page).toHaveURL('/shippers');
    });
  });

  test.describe('ナビバー構造', () => {
    test('全ページで共通ナビバーが表示される', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);

      for (const url of ['/', '/shippers', '/bookings']) {
        await page.goto(url);
        await expect(navbar.brand).toBeVisible();
        await expect(navbar.bookingsLink).toBeVisible();
        await expect(navbar.shippersLink).toBeVisible();
        await expect(navbar.logoutButton).toBeVisible();
      }
    });

    test('ナビバーのリンク順序が 見積管理→予約管理→荷主管理→航路管理 である', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/');

      const navLinks = page.locator('nav .navbar-nav a');
      await expect(navLinks.nth(0)).toHaveText('見積管理');
      await expect(navLinks.nth(1)).toHaveText('予約管理');
      await expect(navLinks.nth(2)).toHaveText('荷主管理');
      await expect(navLinks.nth(3)).toHaveText('航路管理');
    });

    test('ブランドロゴからダッシュボードへ戻れる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);

      await page.goto('/shippers');
      await navbar.clickBrand();

      await expect(page).toHaveURL('/');
    });
  });

  test.describe('ナビバーからの画面遷移', () => {
    test('ダッシュボードから 予約管理 リンクで予約一覧へ遷移できる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/');
      await navbar.clickBookings();

      await expect(page).toHaveURL('/bookings');
    });

    test('ダッシュボードから 荷主管理 リンクで荷主一覧へ遷移できる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/');
      await navbar.clickShippers();

      await expect(page).toHaveURL('/shippers');
    });

    test('荷主管理画面から 予約管理 リンクで予約一覧へ遷移できる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/shippers');
      await navbar.clickBookings();

      await expect(page).toHaveURL('/bookings');
    });

    test('予約管理画面から 荷主管理 リンクで荷主一覧へ遷移できる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/bookings');
      await navbar.clickShippers();

      await expect(page).toHaveURL('/shippers');
    });
  });

  test.describe('アクティブリンクのハイライト', () => {
    test('荷主管理画面では 荷主管理 リンクがアクティブ', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/shippers');

      expect(await navbar.isActiveLink(navbar.shippersLink)).toBe(true);
      expect(await navbar.isActiveLink(navbar.bookingsLink)).toBe(false);
    });

    test('予約管理画面では 予約管理 リンクがアクティブ', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/bookings');

      expect(await navbar.isActiveLink(navbar.bookingsLink)).toBe(true);
      expect(await navbar.isActiveLink(navbar.shippersLink)).toBe(false);
    });

    test('ダッシュボードではどのリンクもアクティブでない', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/');

      expect(await navbar.isActiveLink(navbar.bookingsLink)).toBe(false);
      expect(await navbar.isActiveLink(navbar.shippersLink)).toBe(false);
    });
  });

  test.describe('ログアウト', () => {
    test('ナビバーからログアウトできる', async ({ page, loggedIn }) => {
      const navbar = new NavbarPage(page);
      await page.goto('/');
      await navbar.clickLogout();

      await expect(page).toHaveURL(/\/login/);
    });
  });
});
