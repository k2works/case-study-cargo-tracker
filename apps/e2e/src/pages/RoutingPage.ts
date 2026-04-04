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

  /** 予約 ID を指定して経路設計条件確認ページに遷移する */
  async gotoDesignCondition(bookingId: string) {
    await this.page.goto(`/routings/design-condition?bookingId=${bookingId}`);
  }

  /** 航路一覧ページに遷移する */
  async gotoVoyages() {
    await this.page.goto('/routings/voyages');
  }

  /** 経路設計条件カード */
  get conditionCard(): Locator {
    return this.page.locator('.card.shadow-sm.mb-4');
  }

  /** 「航海スケジュール検索へ進む」リンク */
  get searchFromConditionLink(): Locator {
    return this.page.getByRole('link', { name: '航海スケジュール検索へ進む' });
  }

  /** 「補完依頼を行う」リンク */
  get requestCompletionLink(): Locator {
    return this.page.getByRole('link', { name: '補完依頼を行う' });
  }

  /** 条件不完全の警告アラート */
  get incompleteAlert(): Locator {
    return this.page.locator('.alert.alert-warning');
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

  /** 指定インデックスのルート候補の「この予約に割り当てる」ボタンをクリックする */
  async assignRoute(index: number = 0) {
    const card = this.routeCandidateCard(index);
    await card.locator('button:has-text("この予約に割り当てる")').click();
  }
}
