import { expect, Locator, Page } from '@playwright/test';

/** 予約登録フォームに渡すデータ */
export interface BookingData {
  /** 荷主 UUID */
  shipperId: string;
  /** 貨物種別（例: GENERAL_CARGO, DANGEROUS_GOODS, REFRIGERATED） */
  cargoType: string;
  /** 重量（kg） */
  weightKg: string;
  /** 個数 */
  quantity: string;
  /** 出発地 UNLOCODE（例: JPTYO） */
  originLocation: string;
  /** 目的地 UNLOCODE（例: USNYC） */
  destinationLocation: string;
  /** 希望引渡日（yyyy-MM-dd 形式） */
  requestedPickupDate: string;
  /** 希望着日（yyyy-MM-dd 形式） */
  requestedDeliveryDate: string;
  /** 長さ（cm、省略可） */
  lengthCm?: string;
  /** 幅（cm、省略可） */
  widthCm?: string;
  /** 高さ（cm、省略可） */
  heightCm?: string;
  /** 品名（省略可） */
  description?: string;
  /** UN 番号（危険物のみ） */
  unNumber?: string;
  /** 危険品等級（危険物のみ、省略可） */
  hazardClass?: string;
  /** 最低温度℃（冷凍のみ） */
  minTempCelsius?: string;
  /** 最高温度℃（冷凍のみ） */
  maxTempCelsius?: string;
}

export class BookingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /** 予約登録フォームに遷移する */
  async goto() {
    await this.page.goto('/bookings/new');
  }

  /** 予約一覧に遷移する */
  async gotoList() {
    await this.page.goto('/bookings');
  }

  /** 成功メッセージから予約 UUID を抽出して返す */
  async extractBookingId(): Promise<string> {
    const message = await this.getSuccessMessage();
    const match = message.match(
      /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i,
    );
    if (!match) {
      throw new Error(`予約 ID が成功メッセージから取得できませんでした: ${message}`);
    }
    return match[1];
  }

  /** 予約一覧の対象行を返す */
  bookingRow(bookingId: string): Locator {
    return this.page.locator('tbody tr').filter({ hasText: bookingId });
  }

  /** 予約一覧に登録内容が表示されることを確認する */
  async expectBookingListed(params: {
    bookingId: string;
    shipperName: string;
    cargoType: string;
    originLocation: string;
    destinationLocation: string;
    requestedPickupDate: string;
    status: string;
  }) {
    const row = this.bookingRow(params.bookingId);

    await expect(row).toHaveCount(1);
    await expect(row.locator('td').nth(0)).toContainText(params.bookingId);
    await expect(row.locator('td').nth(1)).toContainText(params.shipperName);
    await expect(row.locator('td').nth(2)).toHaveText(params.cargoType);
    await expect(row.locator('td').nth(3)).toHaveText(params.originLocation);
    await expect(row.locator('td').nth(4)).toHaveText(params.destinationLocation);
    await expect(row.locator('td').nth(5)).toHaveText(params.requestedPickupDate);
    await expect(row.locator('td').nth(6)).toContainText(params.status);
  }

  /**
   * 予約を登録する
   * @param data 予約登録データ
   */
  async register(data: BookingData) {
    await this.goto();

    // 荷主情報
    await this.page.locator('select[name="shipperId"]').selectOption(data.shipperId);

    // 貨物仕様
    await this.page.locator('select[name="cargoType"]').selectOption(data.cargoType);
    await this.page.locator('input[name="weightKg"]').fill(data.weightKg);

    if (data.lengthCm) {
      await this.page.locator('input[name="lengthCm"]').fill(data.lengthCm);
    }
    if (data.widthCm) {
      await this.page.locator('input[name="widthCm"]').fill(data.widthCm);
    }
    if (data.heightCm) {
      await this.page.locator('input[name="heightCm"]').fill(data.heightCm);
    }

    await this.page.locator('input[name="quantity"]').fill(data.quantity);

    if (data.description) {
      await this.page.locator('input[name="description"]').fill(data.description);
    }

    // 危険物固有フィールド
    if (data.unNumber) {
      await this.page.locator('input[name="unNumber"]').fill(data.unNumber);
    }
    if (data.hazardClass) {
      await this.page.locator('input[name="hazardClass"]').fill(data.hazardClass);
    }

    // 冷凍固有フィールド
    if (data.minTempCelsius) {
      await this.page.locator('input[name="minTempCelsius"]').fill(data.minTempCelsius);
    }
    if (data.maxTempCelsius) {
      await this.page.locator('input[name="maxTempCelsius"]').fill(data.maxTempCelsius);
    }

    // 輸送条件
    await this.page.locator('input[name="originLocation"]').fill(data.originLocation);
    await this.page.locator('input[name="destinationLocation"]').fill(data.destinationLocation);
    await this.page.locator('input[name="requestedPickupDate"]').fill(data.requestedPickupDate);
    await this.page.locator('input[name="requestedDeliveryDate"]').fill(data.requestedDeliveryDate);

    await this.page.locator('form[action="/bookings"] button[type="submit"]').click();
  }

  /** 成功メッセージのテキストを返す */
  async getSuccessMessage(): Promise<string> {
    const alert = this.page.locator('.alert-success');
    await alert.waitFor({ state: 'visible' });
    return await alert.innerText();
  }

  /** 予約詳細ページに遷移する */
  async gotoDetail(bookingId: string) {
    await this.page.goto(`/bookings/${bookingId}`);
  }

  /** 「予約を確定する」ボタンをクリックする */
  async confirmBooking() {
    await this.page.locator('button:has-text("予約を確定する")').click();
  }

  /** detail.html から追跡番号テキストを返す（TRK-XXXXXXXX 形式） */
  async getTrackingNumber(): Promise<string> {
    const el = this.page.locator('[data-testid="tracking-number"]');
    await el.waitFor({ state: 'visible' });
    return await el.innerText();
  }
}
