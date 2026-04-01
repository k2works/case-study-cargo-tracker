import { Page } from '@playwright/test';

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

  /**
   * 予約を登録する
   * @param data 予約登録データ
   */
  async register(data: BookingData) {
    await this.goto();

    // 荷主情報
    await this.page.locator('input[name="shipperId"]').fill(data.shipperId);

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
}
