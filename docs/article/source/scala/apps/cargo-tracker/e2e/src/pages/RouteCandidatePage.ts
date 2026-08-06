import { Page } from '@playwright/test';

/** 経路候補画面（US08 表示 / US09 確定） */
export class RouteCandidatePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoCandidates(bookingId: string): Promise<void> {
    await this.page.goto(`/bookings/${bookingId}/routes`);
  }

  /** 期限内候補の N 番目（0-origin）の「この経路で確定」ボタンをクリック */
  async confirmCandidate(index: number = 0): Promise<void> {
    const buttons = this.page.locator('button:has-text("この経路で確定")');
    await buttons.nth(index).click();
  }
}
