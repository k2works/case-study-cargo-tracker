import { Page, Locator } from '@playwright/test';

export class BookingPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly newBookingLink: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', { name: '貨物予約管理' });
    this.newBookingLink = page.getByRole('link', { name: '新規予約' });
    this.submitButton = page.getByRole('button', { name: '登録' });
  }

  async goto() {
    await this.page.goto('/bookings');
  }

  async fillBookingForm(originUnlocode: string, destinationUnlocode: string) {
    // 荷主 ID はデフォルト 1
    // 重量 kg
    await this.page.getByLabel('重量 (kg)').fill('100');
    // 出発地・到着地
    await this.page.getByLabel('出発地 (UNLOCODE)').fill(originUnlocode);
    await this.page.getByLabel('到着地 (UNLOCODE)').fill(destinationUnlocode);
  }

  async submitForm() {
    await this.submitButton.click();
  }
}
