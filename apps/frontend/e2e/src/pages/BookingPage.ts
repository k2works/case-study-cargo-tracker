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

  /**
   * 予約一覧から最初の仮予約リンクをクリックして詳細ページへ遷移する
   */
  async clickFirstPreliminaryBooking() {
    await this.page.getByText('仮予約').first().waitFor();
    // 仮予約バッジと同じ行の予約 ID リンクをクリック
    const row = this.page.locator('tbody tr').filter({ hasText: '仮予約' }).first();
    await row.getByRole('link').first().click();
  }

  /**
   * 経路設計画面で経路を検索して最初の候補を選択・割り当てる
   */
  async searchAndAssignRoute(originUnlocode: string, destinationUnlocode: string) {
    // 経路設計画面が表示されるまで待機
    await this.page.getByRole('heading', { name: /経路設計/ }).waitFor();
    await this.page.getByPlaceholder('JPTYO').fill(originUnlocode);
    await this.page.getByPlaceholder('CNSHA').fill(destinationUnlocode);
    await this.page.getByRole('button', { name: '経路を検索' }).click();
    // 経路候補が表示されるまで待機
    await this.page.getByText('経路候補').waitFor({ timeout: 15000 });
    // 最初の経路候補 div をクリックして選択
    await this.page.locator('.cursor-pointer').first().click();
    // 「この経路を割り当てる」ボタンをクリック
    await this.page.getByRole('button', { name: 'この経路を割り当てる' }).click();
  }
}
