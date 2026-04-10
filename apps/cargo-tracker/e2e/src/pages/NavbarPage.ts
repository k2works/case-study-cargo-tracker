import { Page, Locator } from '@playwright/test';

export class NavbarPage {
  readonly page: Page;
  readonly brand: Locator;
  readonly bookingsLink: Locator;
  readonly shippersLink: Locator;
  readonly billingLink: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.brand = page.locator('nav .navbar-brand');
    this.bookingsLink = page.locator('nav .navbar-nav a', { hasText: '予約管理' });
    this.shippersLink = page.locator('nav .navbar-nav a', { hasText: '荷主管理' });
    this.billingLink = page.locator('nav .navbar-nav a', { hasText: '請求管理' });
    this.logoutButton = page.locator('nav button[type="submit"]', { hasText: 'ログアウト' });
  }

  async clickBrand() {
    await this.brand.click();
  }

  async clickBookings() {
    await this.bookingsLink.click();
  }

  async clickShippers() {
    await this.shippersLink.click();
  }

  async clickBilling() {
    await this.billingLink.click();
  }

  async clickLogout() {
    await this.logoutButton.click();
  }

  isActiveLink(link: Locator): Promise<boolean> {
    return link.evaluate(el => el.classList.contains('active'));
  }
}

export class DashboardPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly bookingsCard: Locator;
  readonly shippersCard: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.bookingsCard = page.locator('a.btn-primary', { hasText: '予約管理へ' });
    this.shippersCard = page.locator('a.btn-primary', { hasText: '荷主管理へ' });
  }

  async goto() {
    await this.page.goto('/');
  }
}
