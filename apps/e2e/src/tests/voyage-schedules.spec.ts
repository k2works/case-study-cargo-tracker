import { test, expect } from '../fixtures';
import { RoutingPage } from '../pages/RoutingPage';

function futureDateStr(monthsAhead: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + monthsAhead);
  return d.toISOString().slice(0, 10);
}

// ─────────────────────────────────────────────────────────────────────────────
// E28〜E29: US20 航海スケジュールを検索する
//
// 受入条件:
//   AC1: 出発地・目的地・期限を検索条件として入力できる
//   AC2: 出発地・目的地は UN/LOCODE 形式で指定できる
//   AC3: 該当する航海情報（航海番号・運送会社・寄港地・出発日・到着日）が一覧表示される
//   AC4: 各寄港地の港湾情報（取扱可能貨物種別）が表示される
//   AC5: 直行便がない場合、寄港地接続による経由ルートの航海候補も表示される
//
// データソース:
//   - E28: V015 シードデータ（SG001〜SG004）を使用
//   - E29: StubRouteProviderAdapter が返す固定候補（SG001/SG002）を使用
// ─────────────────────────────────────────────────────────────────────────────
test.describe('E28〜E29: US20 航海スケジュールを検索する', () => {
  // ─────────────────────────────────────────────────────────────────────────
  // E28: 航路一覧ページで航海スケジュールが一覧表示される（受入条件 3・4・5）
  // ─────────────────────────────────────────────────────────────────────────
  test('E28: 航路一覧ページで全航海スケジュール（航海番号・運送会社・航路・日付・貨物種別）が表示される', async ({
    page,
    loggedIn,
  }) => {
    const routingPage = new RoutingPage(page);
    await routingPage.gotoVoyages();

    // ページ見出しと件数バッジが表示される
    await expect(page.locator('h4')).toContainText('航路一覧');
    await expect(page.locator('h4 .badge')).toBeVisible();

    // AC3: SG001 行 — 航海番号・運送会社・出発地・目的地・出発日・到着日が表示される
    const sg001Row = page.locator('tbody tr').filter({ hasText: 'SG001' });
    await expect(sg001Row).toBeVisible();
    await expect(sg001Row).toContainText('Japan Pacific Lines'); // 運送会社
    await expect(sg001Row).toContainText('JPTYO');               // 出発地
    await expect(sg001Row).toContainText('SGSIN');               // 目的地
    await expect(sg001Row).toContainText('2026-06-01');          // 出発日
    await expect(sg001Row).toContainText('2026-06-15');          // 到着日

    // AC4: SG001 の対応貨物種別バッジが表示される
    await expect(sg001Row).toContainText('一般貨物');
    await expect(sg001Row).toContainText('冷凍・冷蔵');

    // AC5: SG002 行 — 経由港（KRPUS: 釜山）が航路列に表示される（乗り継ぎルート）
    const sg002Row = page.locator('tbody tr').filter({ hasText: 'SG002' });
    await expect(sg002Row).toBeVisible();
    await expect(sg002Row).toContainText('Korea Shipping Corp'); // 運送会社
    await expect(sg002Row).toContainText('KRPUS');               // 経由港（釜山）
  });

  // ─────────────────────────────────────────────────────────────────────────
  // E29: 出発地・目的地を直接指定して検索できる（受入条件 1・2・5）
  // ─────────────────────────────────────────────────────────────────────────
  test('E29: 出発地・目的地を直接指定して経路候補を検索できる', async ({ page, loggedIn }) => {
    const futureDate = futureDateStr(6);

    // 予約 ID なしで検索パラメータを直接指定（AC1: 出発地・目的地・期限を入力できる）
    await page.goto(
      `/routings/search?originLocode=JPTYO&destinationLocode=SGSIN&requestedArrivalDate=${futureDate}&cargoType=GENERAL&weightKg=500`,
    );

    // AC2: 検索条件カードに UN/LOCODE 形式で出発地・目的地が表示される
    const conditionCard = page.locator('.card.shadow-sm.mb-4').first();
    await expect(conditionCard).toContainText('JPTYO');
    await expect(conditionCard).toContainText('SGSIN');
    await expect(conditionCard).toContainText(futureDate);

    // 予約 ID なし直接検索では「予約一覧へ」が表示される（「予約詳細に戻る」ではない）
    await expect(page.getByRole('link', { name: '予約一覧へ' })).toBeVisible();

    // 「この予約に割り当てる」ボタンは表示されない（bookingId なし）
    await expect(
      page.locator('button:has-text("この予約に割り当てる")'),
    ).not.toBeVisible();

    // AC3: 複数の経路候補が表示される
    const routingPage = new RoutingPage(page);
    const count = await routingPage.countCandidates();
    expect(count).toBeGreaterThanOrEqual(2);

    // AC5: 経由ルート候補（SG002: 釜山経由）に中継港 KRPUS が表示される
    const sg002Card = routingPage.routeCandidateCard(1);
    await expect(sg002Card).toContainText('KRPUS');
  });
});
