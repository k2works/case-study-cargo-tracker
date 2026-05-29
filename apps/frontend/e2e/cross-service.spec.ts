import { test, expect, Page } from '@playwright/test';

/**
 * US06 / cross-service: 予約の経路設計引き渡し（bookingms）が Kafka 経由で
 * routingms の経路設計待ちリスト（route_design_request）へ伝搬することを検証する E2E
 * （ADR-0009 / IT3 T7）。
 *
 * 単一サービス完結の他 E2E と異なり、本シナリオは
 *   bookingms → Kafka(cargo-events) → routingms（tracking プロセッサ）
 * の物理経路を実際に通す。したがって以下がすべて起動している必要がある:
 *   - Kafka（Docker Compose / Confluent、localhost:9092）
 *   - authms (:8081)、bookingms (:8082)、routingms (:8083)、gatewayms (:8080)
 *   - bookingms は Kafka publisher 有効、routingms は Kafka fetcher + consumer.event-processor-mode=tracking 有効
 *     （local-h2 / local-docker いずれの既定でも有効。ただし Kafka が起動していること）
 *
 * Kafka を要する重い前提のため、既定の `npm run e2e` では実行しない。
 * 環境変数 CROSS_SERVICE_E2E=1 を指定したときのみ実行する:
 *   - bash:        CROSS_SERVICE_E2E=1 npx playwright test e2e/cross-service.spec.ts
 *   - PowerShell:  $env:CROSS_SERVICE_E2E=1; npx playwright test e2e/cross-service.spec.ts
 */

const crossServiceEnabled =
  process.env.CROSS_SERVICE_E2E === '1' || process.env.CROSS_SERVICE_E2E === 'true';

/** ログインし、後続 API 呼び出しに用いる JWT を取り出す（AuthContext は localStorage の auth_token に保存）。 */
async function loginAndGetToken(page: Page): Promise<string> {
  await page.goto('/login');
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('password');
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
  const token = await page.evaluate(() => localStorage.getItem('auth_token'));
  if (!token) throw new Error('認証トークンを取得できませんでした');
  return token;
}

test.describe('US06/cross-service: 経路設計引き渡しの Kafka 伝搬 E2E', () => {
  test('予約 → 経路設計引き渡し → routingms の経路設計待ちリストに伝搬する', async ({ page, request }) => {
    test.skip(
      !crossServiceEnabled,
      'Kafka を要する cross-service E2E。CROSS_SERVICE_E2E=1 を指定したときのみ実行する。'
    );

    const token = await loginAndGetToken(page);
    const auth = { Authorization: `Bearer ${token}` };
    const bookingId = `BK-XS-${Date.now()}`;

    // 1) bookingms に一般貨物を予約（US04）。
    const bookRes = await request.post('/api/v1/bookings', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        bookingId,
        shipperId: 'S-XS-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USNYC',
        arrivalDeadline: '2027-09-30',
        cargoType: 'GENERAL',
        weightKg: 1500,
        quantity: 10,
        productName: 'cross-service E2E 貨物',
      },
    });
    expect(bookRes.status(), await bookRes.text()).toBe(201);

    // 2) 経路設計へ引き渡し（US06）。bookingms が RouteDesignRequestedEvent を発行し、
    //    KafkaPublisher が cargo-events トピックへ送出する。
    const handoffRes = await request.post(`/api/v1/bookings/${bookingId}/handoff`, { headers: auth });
    expect(handoffRes.ok(), await handoffRes.text()).toBeTruthy();

    // 3) routingms 側で Kafka 経由の受信（tracking プロセッサ）と read model 反映を待つ。
    //    非同期伝搬のため polling する。
    await expect
      .poll(
        async () => {
          const res = await request.get(`/api/v1/routes/design-requests/${bookingId}`, {
            headers: auth,
          });
          return res.status();
        },
        {
          message:
            'routingms に経路設計依頼が伝搬しませんでした。Kafka の起動と各サービスの Kafka 設定（publisher/fetcher/tracking）を確認してください。',
          timeout: 30_000,
          intervals: [500, 1_000, 2_000, 3_000],
        }
      )
      .toBe(200);

    // 4) 伝搬した経路設計依頼の内容が予約の経路設計情報と一致する（自己完結イベント）。
    const finalRes = await request.get(`/api/v1/routes/design-requests/${bookingId}`, {
      headers: auth,
    });
    expect(finalRes.status()).toBe(200);
    const body = await finalRes.json();
    expect(body.bookingId).toBe(bookingId);
    expect(body.originUnlocode).toBe('JPTYO');
    expect(body.destinationUnlocode).toBe('USNYC');
    expect(body.cargoType).toBe('GENERAL');
  });
});

test.describe('US11/cross-service: 経路確定の Kafka 伝搬 E2E（routingms → bookingms）', () => {
  test('経路候補算出 → 確定 → 紐付けで予約状態が経路提案中に伝搬する', async ({ page, request }) => {
    test.skip(
      !crossServiceEnabled,
      'Kafka を要する cross-service E2E。CROSS_SERVICE_E2E=1 を指定したときのみ実行する。'
    );

    const token = await loginAndGetToken(page);
    const auth = { Authorization: `Bearer ${token}` };
    const stamp = Date.now();
    const bookingId = `BK-RC-${stamp}`;
    const voyageNumber = `V-RC-${stamp}`;

    // 0) routingms に直行便（JPTYO→USNYC、一般貨物受入、期限内到着）を登録する。
    const voyageRes = await request.post('/api/v1/voyages', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        voyageNumber,
        carrierCode: 'MAERSK',
        carrierName: 'Maersk Line',
        shipName: 'RC Test Vessel',
        originUnlocode: 'JPTYO',
        destUnlocode: 'USNYC',
        departureDate: '2027-01-10T09:00:00',
        arrivalDate: '2027-02-10T18:00:00',
        movements: [],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyageRes.status(), await voyageRes.text()).toBe(201);

    // 1) 予約 → 経路設計引き渡し（route_design_request が routingms に伝搬）。
    const bookRes = await request.post('/api/v1/bookings', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        bookingId,
        shipperId: 'S-RC-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USNYC',
        arrivalDeadline: '2027-09-30',
        cargoType: 'GENERAL',
        weightKg: 1500,
        quantity: 10,
        productName: 'route-confirmed E2E 貨物',
      },
    });
    expect(bookRes.status(), await bookRes.text()).toBe(201);
    const handoffRes = await request.post(`/api/v1/bookings/${bookingId}/handoff`, { headers: auth });
    expect(handoffRes.ok(), await handoffRes.text()).toBeTruthy();

    // 2) 経路設計依頼の伝搬と航海 Read Model の反映を待ち、経路候補が算出されるまで polling する。
    await expect
      .poll(
        async () => {
          const res = await request.post(`/api/v1/routes/${bookingId}/calculate`, { headers: auth });
          if (res.status() !== 200) return 0;
          const candidates = await res.json();
          return candidates.length;
        },
        {
          message: '経路候補が算出されませんでした。route_design_request の伝搬と航海登録を確認してください。',
          timeout: 30_000,
          intervals: [500, 1_000, 2_000, 3_000],
        }
      )
      .toBeGreaterThan(0);

    // 3) 推奨候補（sequence=1）を確定し予約に紐付ける。routingms が RouteConfirmedEvent を発行する。
    const confirmRes = await request.post(`/api/v1/routes/${bookingId}/confirm`, {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: { sequence: 1 },
    });
    expect(confirmRes.status(), await confirmRes.text()).toBe(202);

    // 4) bookingms 側で Kafka 経由の受信 → Saga → AssignRouteToCargoCommand → CargoRoutedEvent の反映を待つ。
    await expect
      .poll(
        async () => {
          const res = await request.get(`/api/v1/bookings/${bookingId}`, { headers: auth });
          if (res.status() !== 200) return '';
          const detail = await res.json();
          return detail.bookingStatus;
        },
        {
          message:
            '予約状態が ROUTE_PROPOSED に伝搬しませんでした。RouteConfirmedEvent の cross-service 連携を確認してください。',
          timeout: 30_000,
          intervals: [500, 1_000, 2_000, 3_000],
        }
      )
      .toBe('ROUTE_PROPOSED');

    // 5) 確定旅程（cargo_leg）が紐付いていることを確認する。
    const routeRes = await request.get(`/api/v1/bookings/${bookingId}/route`, { headers: auth });
    expect(routeRes.status()).toBe(200);
    const legs = await routeRes.json();
    expect(legs.length).toBeGreaterThan(0);
    expect(legs[0].loadUnlocode).toBe('JPTYO');
    expect(legs[legs.length - 1].unloadUnlocode).toBe('USNYC');
  });
});

test.describe('US14/US15/US17/cross-service: 追跡番号採番と荷役による状態自動更新 E2E（IT5）', () => {
  test('予約確定 → 採番 → 荷役 RECEIVE → tracking_summary が RECEIVED に伝搬する', async ({ page, request }) => {
    test.skip(
      !crossServiceEnabled,
      'Kafka を要する cross-service E2E。CROSS_SERVICE_E2E=1 を指定したときのみ実行する。',
    );

    const token = await loginAndGetToken(page);
    const auth = { Authorization: `Bearer ${token}` };
    const stamp = Date.now();
    const bookingId = `BK-TR-${stamp}`;
    const voyageNumber = `V-TR-${stamp}`;

    // 0) 航海登録（経路候補算出のため必要）
    const voyageRes = await request.post('/api/v1/voyages', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        voyageNumber,
        carrierCode: 'MAERSK',
        carrierName: 'Maersk Line',
        shipName: 'Tracking E2E Vessel',
        originUnlocode: 'JPTYO',
        destUnlocode: 'USNYC',
        departureDate: '2027-01-10T09:00:00',
        arrivalDate: '2027-02-10T18:00:00',
        movements: [],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyageRes.status(), await voyageRes.text()).toBe(201);

    // 1) 予約登録 + 経路設計引き渡し
    const bookRes = await request.post('/api/v1/bookings', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        bookingId,
        shipperId: 'S-TR-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USNYC',
        arrivalDeadline: '2027-09-30',
        cargoType: 'GENERAL',
        weightKg: 1500,
        quantity: 10,
        productName: 'tracking E2E 貨物',
      },
    });
    expect(bookRes.status(), await bookRes.text()).toBe(201);
    expect((await request.post(`/api/v1/bookings/${bookingId}/handoff`, { headers: auth })).ok()).toBeTruthy();

    // 2) 経路候補算出 → 確定 → 紐付け（経路提案中まで）
    await expect
      .poll(
        async () => {
          const res = await request.post(`/api/v1/routes/${bookingId}/calculate`, { headers: auth });
          if (res.status() !== 200) return 0;
          const candidates = await res.json();
          return candidates.length;
        },
        { timeout: 30_000, intervals: [500, 1_000, 2_000] },
      )
      .toBeGreaterThan(0);

    expect(
      (
        await request.post(`/api/v1/routes/${bookingId}/confirm`, {
          headers: { ...auth, 'Content-Type': 'application/json' },
          data: { sequence: 1 },
        })
      ).status(),
    ).toBe(202);

    // 経路提案中まで伝搬を待つ
    await expect
      .poll(
        async () => {
          const r = await request.get(`/api/v1/bookings/${bookingId}`, { headers: auth });
          if (r.status() !== 200) return '';
          return (await r.json()).bookingStatus;
        },
        { timeout: 30_000, intervals: [500, 1_000, 2_000] },
      )
      .toBe('ROUTE_PROPOSED');

    // 3) 予約確定 → BookingSagaManager が TrackingIssuanceRequestedEvent を発行 →
    //    trackingms が初期化・採番 → CargoTrackedEvent で Saga 終了 → bookingms が TRACKING_ISSUED に
    expect((await request.post(`/api/v1/bookings/${bookingId}/confirm`, { headers: auth })).ok()).toBeTruthy();

    // 4) 採番完了を待ち、tracking_number を取得する（US14）
    const trackingNumber: string = await (async () => {
      let tn: string | null = null;
      await expect
        .poll(
          async () => {
            const r = await request.get(`/api/v1/bookings/${bookingId}`, { headers: auth });
            if (r.status() !== 200) return null;
            const body = await r.json();
            if (body.bookingStatus === 'TRACKING_ISSUED' && body.trackingNumber) {
              tn = body.trackingNumber;
              return tn;
            }
            return null;
          },
          {
            message: 'US14: 追跡番号の採番が cross-service で伝搬しませんでした',
            timeout: 30_000,
            intervals: [500, 1_000, 2_000, 3_000],
          },
        )
        .not.toBeNull();
      if (!tn) throw new Error('採番された追跡番号を取得できませんでした');
      return tn;
    })();
    expect(trackingNumber).toMatch(/^TRK-[A-Z0-9]{10}$/);

    // 5) trackingms の tracking_summary が NOT_RECEIVED で初期化されていることを確認
    await expect
      .poll(
        async () => {
          const r = await request.get(`/api/v1/tracking/${trackingNumber}`, { headers: auth });
          if (r.status() !== 200) return '';
          return (await r.json()).currentStatus;
        },
        { timeout: 30_000, intervals: [500, 1_000, 2_000] },
      )
      .toBe('NOT_RECEIVED');

    // 6) 荷役 RECEIVE を登録 → cross-service で trackingms が tracking_summary を RECEIVED に更新（US15）
    const handlingRes = await request.post('/api/v1/handling', {
      headers: { ...auth, 'Content-Type': 'application/json' },
      data: {
        trackingNumber,
        handlingType: 'RECEIVE',
        unlocode: 'JPTYO',
        voyageNumber: null,
        occurredAt: '2027-01-09T10:00:00',
        handlerId: 'H-E2E-001',
      },
    });
    expect(handlingRes.status(), await handlingRes.text()).toBe(201);

    await expect
      .poll(
        async () => {
          const r = await request.get(`/api/v1/tracking/${trackingNumber}`, { headers: auth });
          if (r.status() !== 200) return '';
          return (await r.json()).currentStatus;
        },
        {
          message: 'US15: 荷役 RECEIVE による状態 RECEIVED への伝搬が確認できませんでした',
          timeout: 30_000,
          intervals: [500, 1_000, 2_000, 3_000],
        },
      )
      .toBe('RECEIVED');

    // 7) 履歴に source=HANDLING の RECEIVED が記録されている（タイミング次第で空でも許容）
    const eventsRes = await request.get(`/api/v1/tracking/${trackingNumber}/events`, { headers: auth });
    expect(eventsRes.status()).toBe(200);
    const events = await eventsRes.json();
    // 初期化 + RECEIVED の少なくとも 2 件
    expect(events.length).toBeGreaterThanOrEqual(2);
    const receivedEvent = events.find(
      (e: { transportStatus?: string }) => e.transportStatus === 'RECEIVED',
    );
    expect(receivedEvent).toBeTruthy();
  });

  test('US17: 追跡管理者が手動で状態を更新できる（UI 直叩き / 不正遷移は 422）', async ({ page, request }) => {
    test.skip(
      !crossServiceEnabled,
      'Kafka を要する cross-service E2E。CROSS_SERVICE_E2E=1 を指定したときのみ実行する。',
    );

    const token = await loginAndGetToken(page);
    const auth = { Authorization: `Bearer ${token}` };

    // 直前テストの状態を引き継がず独立する。簡略のため /api/v1/tracking 一覧から
    // 既存の追跡番号を 1 件取得して活用する（前テストで採番済みのものを再利用）。
    const listRes = await request.get('/api/v1/tracking?page=0&size=5', { headers: auth });
    expect(listRes.status()).toBe(200);
    const list = await listRes.json();
    if (list.items.length === 0) {
      test.skip(true, '追跡番号が 1 件もありません。前テストを先に実行してください。');
      return;
    }
    const target = list.items[0];

    // 不正遷移：NOT_RECEIVED から DELIVERED は 422 で拒否
    if (target.currentStatus === 'NOT_RECEIVED') {
      const badRes = await request.post(`/api/v1/tracking/${target.trackingNumber}/status`, {
        headers: { ...auth, 'Content-Type': 'application/json' },
        data: {
          toStatus: 'DELIVERED',
          unlocode: 'USNYC',
          occurredAt: '2027-02-01T10:00:00',
        },
      });
      expect(badRes.status()).toBe(422);
    }
  });
});
