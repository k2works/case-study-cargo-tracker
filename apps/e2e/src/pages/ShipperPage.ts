import { Page } from '@playwright/test';

export class ShipperPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /** 荷主登録フォームに遷移する */
  async goto() {
    await this.page.goto('/shippers/new');
  }

  /** 荷主一覧に遷移する */
  async gotoList() {
    await this.page.goto('/shippers');
  }

  /**
   * 個人荷主を登録する
   * @param name 氏名
   * @param email メールアドレス
   * @param phone 電話番号（省略可）
   */
  async registerIndividual(name: string, email: string, phone?: string) {
    await this.goto();
    // INDIVIDUAL ラジオボタンをチェック（デフォルトで選択済みだが明示的に選択する）
    await this.page.locator('input[name="category"][value="INDIVIDUAL"]').check();
    await this.page.locator('input[name="name"]').fill(name);
    await this.page.locator('input[name="email"]').fill(email);
    if (phone) {
      await this.page.locator('input[name="phone"]').fill(phone);
    }
    await this.page.locator('form[action="/shippers"] button[type="submit"]').click();
  }

  /**
   * 法人荷主を登録する
   * @param name 社名
   * @param email メールアドレス
   * @param phone 電話番号（省略可）
   * @param contractNumber 契約番号（省略可）
   * @param discountRate 割引率（省略可）
   */
  async registerCorporate(
    name: string,
    email: string,
    phone?: string,
    contractNumber?: string,
    discountRate?: string,
  ) {
    await this.goto();
    // CORPORATE を選択して法人情報セクションを表示する
    await this.page.locator('input[name="category"][value="CORPORATE"]').check();
    // JavaScript による DOM 操作の完了を待機する
    await this.page.locator('#corporateSection').waitFor({ state: 'visible' });

    await this.page.locator('input[name="name"]').fill(name);
    await this.page.locator('input[name="email"]').fill(email);
    if (phone) {
      await this.page.locator('input[name="phone"]').fill(phone);
    }
    if (contractNumber) {
      await this.page.locator('input[name="contractNumber"]').fill(contractNumber);
    }
    if (discountRate) {
      await this.page.locator('input[name="discountRate"]').fill(discountRate);
    }
    await this.page.locator('form[action="/shippers"] button[type="submit"]').click();
  }

  /** 成功メッセージのテキストを返す */
  async getSuccessMessage(): Promise<string> {
    const alert = this.page.locator('.alert-success');
    await alert.waitFor({ state: 'visible' });
    return await alert.innerText();
  }

  /**
   * 成功メッセージから荷主 UUID を抽出して返す
   * 例: "荷主を登録しました（ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx）"
   */
  async extractShipperId(): Promise<string> {
    const message = await this.getSuccessMessage();
    const match = message.match(
      /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i,
    );
    if (!match) {
      throw new Error(`荷主 ID が成功メッセージから取得できませんでした: ${message}`);
    }
    return match[1];
  }
}
