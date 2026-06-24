import { Page } from '@playwright/test';

/** 監査ログ画面 (IT9 US30) のページオブジェクト。MasterAdmin 限定。 */
export class AuditLogPage {
  constructor(public readonly page: Page) {}

  async gotoList(): Promise<void> {
    await this.page.goto('/admin/audit-logs');
  }

  /** ナビバーの「マスタ管理」dropdown → 「監査ログ」リンク経由でアクセス (UI 動線確認用)。 */
  async openFromNavDropdown(): Promise<void> {
    await this.page.locator('.dropdown-toggle:has-text("マスタ管理")').click();
    await this.page.locator('.dropdown-item:has-text("監査ログ")').click();
  }

  async filterByAction(action: string): Promise<void> {
    await this.page.locator('select[name="action"]').selectOption(action);
    await this.page.locator('button:has-text("検索")').click();
  }
}
