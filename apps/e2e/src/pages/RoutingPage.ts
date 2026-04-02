import { expect, Locator, Page } from '@playwright/test';

export class RoutingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /** 予約 ID を指定してルート検索ページに遷移する */
  async gotoByBookingId(bookingId: string) {
    await this.page.goto(`/routings/search?bookingId=${bookingId}`);
  }

  /** N 番目（0 始まり）のルート候補カードを返す */
  routeCandidateCard(index: number): Locator {
    return this.page.locator('.card.shadow-sm.mb-3').nth(index);
  }

  /** ルート候補カードの数を返す */
  async countCandidates(): Promise<number> {
    return await this.page.locator('.card.shadow-sm.mb-3').count();
  }

  /** 指定インデックスのルート候補が表示されていることを確認する */
  async expectCandidateVisible(params: {
    index: number;
    voyageNumber: string;
    transitDaysText: string;
    estimatedPriceText: string;
  }) {
    const card = this.routeCandidateCard(params.index);
    await expect(card).toBeVisible();
    await expect(card.locator('.badge.bg-primary')).toHaveText(params.voyageNumber);
    await expect(card).toContainText(params.transitDaysText);
    await expect(card).toContainText(params.estimatedPriceText);
  }
}
