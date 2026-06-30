import { test, expect } from '@playwright/test';

// IT3 で実装した 3 ストーリーのハッピーパス E2E:
// - US07: 航海スケジュール検索 (/voyages/search)
// - US08a: 経路候補算出 (/bookings/:id/routes)
// - US27: 通関情報紐付け (/bookings/:id/customs)
//
// 前提:
// - shipper/booking/voyage の登録は IT2 の spec を踏襲
// - PRG パターンと UnLocode (5 文字大文字英字) を共有
// - 経路候補は事前登録された航海が必要なため、US07 で使う航海を先に登録する

const PORT_FROM = 'JPTYO';
const PORT_TO = 'USNYC';

async function registerShipper(page: import('@playwright/test').Page): Promise<string> {
  const email = `it3.${Date.now()}.${Math.random().toString(36).slice(2, 6)}@example.com`;
  await page.goto('/shippers/new');
  await page.locator('#name').fill('IT3 E2E 用 荷主');
  await page.locator('#email').fill(email);
  await page.locator('#address').fill('Tokyo');
  await page.locator('#kind-individual').check();
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/shippers\/SHP-[A-Z0-9]{6}$/);
  return page.url().split('/').pop()!;
}

async function registerVoyage(
  page: import('@playwright/test').Page,
  from: string,
  to: string,
  depart: string,
  arrive: string,
): Promise<string> {
  const voyageNumber = `V${Date.now().toString().slice(-6)}${Math.floor(Math.random() * 10)}`;
  await page.goto('/voyages/new');
  await page.locator('#voyageNumber').fill(voyageNumber);
  await page.locator('select[name="movement1Departure"]').selectOption(from);
  await page.locator('select[name="movement1Arrival"]').selectOption(to);
  await page.locator('input[name="movement1DepartureTime"]').fill(depart);
  await page.locator('input[name="movement1ArrivalTime"]').fill(arrive);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(new RegExp(`/voyages/${voyageNumber}$`));
  return voyageNumber;
}

async function registerBooking(
  page: import('@playwright/test').Page,
  shipperId: string,
  deadline: string,
): Promise<string> {
  await page.goto('/bookings/new');
  await page.locator('#shipperId').fill(shipperId);
  await page.locator('#origin').selectOption(PORT_FROM);
  await page.locator('#destination').selectOption(PORT_TO);
  await page.locator('#deadline').fill(deadline);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/bookings\/BK-[A-Z0-9]{6}$/);
  return page.url().split('/').pop()!;
}

test.describe('US07 航海スケジュール検索 (IT3)', () => {
  test('登録済の航海を origin/destination で検索すると結果テーブルに表示される', async ({ page }) => {
    const voyageNumber = await registerVoyage(
      page,
      PORT_FROM,
      PORT_TO,
      '2026-09-10T09:00',
      '2026-09-25T18:00',
    );

    await page.goto('/voyages/search');
    await expect(page.locator('h1')).toContainText('航海スケジュール検索');

    await page.locator('#from').fill(PORT_FROM);
    await page.locator('#to').fill(PORT_TO);
    await page.locator('#from_date').fill('2026-09-01');
    await page.locator('#to_date').fill('2026-09-30');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(/\/voyages\/search\?/);
    await expect(page.locator('table')).toContainText(voyageNumber);
  });

  test('該当航海ゼロのクエリでは「該当する航海がありません」が表示される', async ({ page }) => {
    await page.goto('/voyages/search');
    await page.locator('#from').fill('GBLON');
    await page.locator('#to').fill('SGSIN');
    await page.locator('#from_date').fill('2099-01-01');
    await page.locator('#to_date').fill('2099-01-31');
    await page.locator('button[type="submit"]').click();

    await expect(page.locator('.alert-warning')).toContainText('該当する航海がありません');
  });
});

test.describe('US08a 経路候補算出 (IT3)', () => {
  test('予約 + 期日内航海登録済の状態で /bookings/:id/routes が候補表を返す', async ({ page }) => {
    const voyageNumber = await registerVoyage(
      page,
      PORT_FROM,
      PORT_TO,
      '2026-10-05T08:00',
      '2026-10-20T17:00',
    );

    const shipperId = await registerShipper(page);
    const bookingId = await registerBooking(page, shipperId, '2026-10-31T23:00');

    await page.goto(`/bookings/${bookingId}/routes`);
    await expect(page.locator('h1')).toContainText('経路候補');
    await expect(page.locator('body')).toContainText(voyageNumber);
  });
});

test.describe('US27 通関情報紐付け (IT3)', () => {
  test('予約詳細から通関情報を登録すると詳細画面に HS コード / 業者名が表示される', async ({ page }) => {
    const shipperId = await registerShipper(page);
    const bookingId = await registerBooking(page, shipperId, '2026-11-30T23:00');

    await page.goto(`/bookings/${bookingId}/customs/edit`);
    await expect(page.locator('h1')).toContainText('通関情報編集');

    await page.locator('#hs_code').fill('640391');
    await page.locator('#broker_name').fill('E2E 通関業者株式会社');
    await page.locator('#status').selectOption('CLEARED');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingId}(\\?.*)?$`));
    const body = page.locator('body');
    await expect(body).toContainText('640391');
    await expect(body).toContainText('E2E 通関業者株式会社');
    await expect(body).toContainText('CLEARED');
  });

  test('HS コード 5 桁などの不正値はエラーフラッシュで再表示される', async ({ page }) => {
    const shipperId = await registerShipper(page);
    const bookingId = await registerBooking(page, shipperId, '2026-12-15T23:00');

    await page.goto(`/bookings/${bookingId}/customs/edit`);
    await page.locator('#hs_code').evaluate((el: HTMLInputElement) => {
      el.removeAttribute('pattern');
      el.removeAttribute('minlength');
    });
    await page.locator('#hs_code').fill('12345');
    await page.locator('#broker_name').fill('Broker');
    await page.locator('button[type="submit"]').click();

    await expect(page).toHaveURL(new RegExp(`/bookings/${bookingId}/customs/edit`));
    await expect(page.locator('.alert-danger')).toBeVisible();
  });
});
