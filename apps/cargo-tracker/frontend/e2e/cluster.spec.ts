import { expect, test } from '@playwright/test';

/**
 * kind クラスタに対して実際に配ったものを踏む（IT2 の DoD）。
 *
 * <p>単体テストもモックの E2E も、「実際に配ったものが動くか」を判別しない。
 * イメージの作り忘れ・マイグレーションの失敗・サービス間の配線ミスは、
 * ここでしか出ない。</p>
 *
 * <p>`E2E_BASE_URL` が無いときは<b>読み込まない</b>（`playwright.config.ts` の
 * `testIgnore`）。skip にすると「飛ばした」のか「無い」のかが実行結果から
 * 読み取れず、0 件で緑の回が混じる。</p>
 */
test.describe('kind クラスタでの通し確認', () => {
  /** 業務タイムゾーンで作る。toISOString() は CI（UTC）で 1 日ずれる。 */
  function businessDate(offsetDays: number): string {
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    const at = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
    return formatter.format(at);
  }

  /**
   * 実行ごとに違う航海番号を作る。
   *
   * <p><b>epoch の下 6 桁にしない。</b> 約 16.7 分で一巡するので、短い間に何度も
   * 回すと過去の実行と衝突する。衝突した登録は集約が断り（不変条件 1）、その回の
   * テストは前の回の航海を触ることになる（実測: US25 が 1 度落ちた）。</p>
   *
   * <p>航海番号は 20 文字まで。基数 36 にすると 8 文字で収まる。</p>
   */
  function uniqueVoyageNumber(prefix: string): string {
    return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 36).toString(36)}`;
  }

  async function signIn(page: import('@playwright/test').Page, username: string) {
    await page.goto('/login');
    await page.getByLabel('利用者名').fill(username);
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('営業が荷主と貨物予約を登録し、一覧と詳細に出る', async ({ page }) => {
    const stamp = Date.now();
    const email = `cluster-${stamp}@example.com`;
    const product = `クラスタ確認-${stamp}`;

    await signIn(page, 'sales01');

    // 荷主を登録する。
    await page.goto('/shippers/new');
    await page.getByLabel('名称').fill(`クラスタ商事 ${stamp}`);
    await page.getByLabel('メールアドレス').fill(email);
    await page.getByLabel('電話番号').fill('03-0000-0000');
    await page.getByLabel('住所').fill('東京都中央区');
    await page.getByRole('button', { name: '登録する' }).click();

    // 投影は非同期なので、一覧に出るまで待つ。
    await expect(page.getByText(email)).toBeVisible({ timeout: 20_000 });

    await page.getByRole('link', { name: '予約登録' }).first().click();
    await expect(page.getByRole('heading', { name: '貨物予約の登録' })).toBeVisible();

    // 荷主は選ぶ。識別子を打たせると、営業は一覧を開いて UUID を書き写すことになる。
    // 選択肢は「名称（荷主コード）」なので、名称の部分で当てる。
    const option = page.locator('#shipperId option', { hasText: `クラスタ商事 ${stamp}` });
    await expect(option).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主').selectOption(await option.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('USNYC');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill(product);
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧に出る（予約番号が採番され、状態は仮受付）。
    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
    const row = page.locator('tr', { hasText: product });
    await expect(row.getByText('仮受付')).toBeVisible();

    // 詳細まで開く。一覧に出るだけでは、詳細の配線が通っているか分からない。
    await row.getByRole('link').first().click();
    await expect(page.getByRole('heading', { name: /^予約 B-/ })).toBeVisible();
    await expect(page.getByText(product)).toBeVisible();
    await expect(page.getByText('120 × 80 × 100 cm')).toBeVisible();
  });

  test('集約が断ると理由が出る（500 にならない）', async ({ page }) => {
    await signIn(page, 'sales01');
    await page.goto('/bookings/new');

    // 荷主は一覧の先頭を選ぶ。ここで見たいのは経路の拒否なので、誰でもよい。
    const first = page.locator('#shipperId option').nth(1);
    await expect(first).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主').selectOption(await first.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('JPTYO');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill('同一港');
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByRole('alert')).toContainText('出発地と目的地が同じ');
  });

  /**
   * 資格情報を取る。画面から入り直すより速く、前提づくりの手順が本文から消える。
   */
  async function tokenOf(
    request: import('@playwright/test').APIRequestContext,
    username: string,
  ): Promise<string> {
    const response = await request.post('/api/v1/auth/login', {
      data: { username, password: 'secret1234' },
    });
    expect(response.status()).toBe(200);
    return (await response.json()).token as string;
  }

  /**
   * 仮受付の予約を 1 件作る。
   *
   * <p><b>各テストが自分で前提を作る</b>（IT2 引き継ぎ 7）。前のテストが残したものに
   * 頼ると、実行順を変えたときに落ち、<b>前回実行の残骸でも緑になる</b>。残骸で緑に
   * なる検査は、壊れていることを教えてくれない。</p>
   */
  async function bookCargo(
    request: import('@playwright/test').APIRequestContext,
    product: string,
  ): Promise<string> {
    const token = await tokenOf(request, 'sales01');
    const headers = { Authorization: `Bearer ${token}` };
    const stamp = Date.now();

    const shipper = await request.post('/api/v1/booking/shippers', {
      headers,
      data: {
        name: `前提商事 ${stamp}`,
        shipperType: 'INDIVIDUAL',
        email: `precondition-${stamp}@example.com`,
        phone: '03-0000-0000',
        address: '東京都中央区',
        acknowledgedDuplicate: false,
      },
    });
    expect(shipper.status()).toBe(201);

    const booking = await request.post('/api/v1/booking/bookings', {
      headers,
      data: {
        shipperId: (await shipper.json()).shipperId,
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        arrivalDeadline: businessDate(60),
        cargoType: 'GENERAL',
        weightKg: '1200',
        lengthCm: '120',
        widthCm: '80',
        heightCm: '100',
        quantity: 10,
        productName: product,
      },
    });
    expect(booking.status()).toBe(201);
    return (await booking.json()).bookingId as string;
  }

  test('営業には引き渡していない予約の件数と導線が出る', async ({ page, request }) => {
    // 前提は自分で作る。前のテストに頼ると、単独で回したときに落ちる。
    await bookCargo(request, `件数確認-${Date.now()}`);

    // 引き渡すのは営業の仕事（US06）。経路設計者にこの件数を出しても、
    // その件数に対して打てる手が無い。
    await signIn(page, 'sales01');

    const notice = page.getByText(/経路設計者へ引き渡していない予約が \d+ 件/);
    await expect(notice).toBeVisible({ timeout: 20_000 });
    await notice.getByRole('link', { name: '予約一覧' }).click();

    await expect(page.getByRole('heading', { name: '予約一覧' })).toBeVisible();
  });

  test('営業が経路設計へ引き渡すと、経路設計者の作業一覧に出る（US06）', async ({
    page,
    request,
  }) => {
    const product = `引き渡し-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.getByRole('link', { name: '経路設計作業' }).first().click();

    await expect(page.getByRole('heading', { name: '経路設計作業一覧' })).toBeVisible();
    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
  });

  test('経路設計者が航海を登録すると、一覧に出る（US24）', async ({ page }) => {
    const voyageNumber = uniqueVoyageNumber('V-E2E-');

    await signIn(page, 'routing01');
    await page.getByRole('link', { name: '航海登録' }).first().click();
    await expect(page.getByRole('heading', { name: '航海スケジュールを登録する' })).toBeVisible();

    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('MOL');
    await page.getByLabel('運送会社名', { exact: true }).fill('商船三井');
    await page.getByLabel('船名').fill('E2E EXPRESS');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('USNYC');
    // 出港済みを既定で外すので、未来の日付にしないと一覧に出ない。
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(45)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByRole('heading', { name: '航海スケジュール一覧' })).toBeVisible();
    await expect(page.getByText(voyageNumber)).toBeVisible({ timeout: 20_000 });
    // 船名だけで当てない。同じ船名の航海が何度目かの実行で積み上がっており、
    // 「見えている」のは別の回に登録した行かもしれない。登録した行の中で見る。
    await expect(page.locator('tr', { hasText: voyageNumber })).toContainText('E2E EXPRESS');
  });

  test('経路設計者が候補を見て経路を確定する（US08・US09・IT5）', async ({ page, request }) => {
    // **サービス越しの問い合わせは、この確認でしか出ない失敗の宝庫。**
    // 単体もモックも「届く」ことは見ていない。
    const voyageNumber = uniqueVoyageNumber('V-RT-');
    const product = `経路設計-${Date.now()}`;

    // 候補になる航海を先に登録する。無いと候補 0 件になり、
    // 「届いていない」のか「便が無い」のか分からない。
    const routingToken = await tokenOf(request, 'routing01');
    const voyage = await request.post('/api/v1/routing/voyages', {
      headers: { Authorization: `Bearer ${routingToken}` },
      data: {
        voyageNumber,
        carrierCode: 'MOL',
        carrierName: '商船三井',
        vesselName: 'ROUTE EXPRESS',
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USNYC',
            // 業務タイムゾーンで作る。UTC で作ると CI の時間帯で 1 日ずれる。
            //
            // **どの候補より速くする。** 過去の実行で JPTYO → USNYC の航海が
            // 積み上がっており、候補は 20 件で打ち切られる（ADR-0007）。
            // 遅い便を登録すると、届いているのに押し出されて「出ない」になる
            // （実測: 所要 20 日で登録し、既存の 16 日の便 20 件に押し出された）。
            departureAt: `${businessDate(2)}T00:00:00Z`,
            arrivalAt: `${businessDate(5)}T00:00:00Z`,
          },
        ],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyage.status()).toBe(201);

    const bookingId = await bookCargo(request, product);

    // 引き渡す（US06）。引き渡していない予約には経路を確定できない。
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.getByRole('link', { name: '経路設計作業' }).first().click();
    // 作業一覧の予約番号は経路設計ワークベンチ（S31）を開く。
    await page.locator('tr', { hasText: product }).getByRole('link').first().click();

    await expect(page.getByRole('heading', { name: '経路候補' })).toBeVisible();

    // **順位で当てない。** 過去の実行で同じ所要日数の便が積み上がっており、
    // 同着の並びは決まらない（実測: 1 位が前回の回の便だった）。登録した
    // 航海の行を名指しで探す。出なければ、届いていないか探索が落としている。
    // **自分が作った航海を名指しで探さない。** 探索は推奨順の上位 20 件で
    // 打ち切る（ADR-0007）ので、同じ区間の航海が 20 本を超えると自分の 1 本が
    // 出るとは限らない。クラスタは作り直さずに使い続けるため、実行のたびに
    // 航海が積み上がる（IT7 の 2 度目の通しで実測。84 本あった）。
    // 経路設計者がするのは「候補を見て 1 つ選ぶ」ことなので、先頭の候補を選ぶ。
    const candidate = page.getByTestId('candidate-1');
    await expect(candidate).toBeVisible({ timeout: 30_000 });
    await candidate.getByRole('radio').check();
    await page.getByRole('button', { name: 'この経路で確定' }).click();

    // 確定すると予約詳細へ戻り、旅程が読める（US09）。
    // **選んだ候補の航海番号で見る。** 自分が登録した航海とは限らない
    // （上位 20 件で打ち切るため。ADR-0007）。空でないことまで確かめる。
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('leg-1')).toContainText(/V-/);
    // 荷主に通知するまでは提案中（US12）。ここが確定になってはいけない。
    await expect(page.getByText('経路提案中')).toBeVisible();
  });

  test('経路設計者が条件を調整して再算出できる（US10・IT6）', async ({ page, request }) => {
    // デモ項目 2。**条件はサーバが持つ**ので、画面で変えて再算出すると調整が
    // 記録され、候補算出はその条件から組み直される。
    const product = `条件調整-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);

    // いまの条件が読める（読めないと同じ条件で何度も回す）。
    await expect(page.getByLabel('到着期限')).toBeVisible({ timeout: 20_000 });
    await page.getByLabel('除外する港').fill('SGSIN');
    await page.getByRole('button', { name: '条件を変えて再算出' }).click();

    // 調整が記録され、読み直した条件が欄に戻る。
    await expect(page.getByLabel('除外する港')).toHaveValue('SGSIN', { timeout: 20_000 });
  });

  test('経路設計者が営業へ差し戻すと、営業のダッシュボードに出る（US10・IT6）',
      async ({ page, request }) => {
    // デモ項目 3。**差し戻しても状態は動かない**（ADR-0009 決定 1）ので、
    // 予約は経路設計の作業一覧に残ったまま、営業に「見直してほしい」が届く。
    const product = `差し戻し-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);
    await page.getByRole('button', { name: '営業へ差し戻す' }).click();
    await page.getByLabel('差し戻す理由').fill(`期限内に着ける便がありません（${product}）`);
    await page.getByRole('button', { name: '差し戻しを送る' }).click();

    await page.goto('/logout');
    await signIn(page, 'sales01');
    // 件数だけでは仕事が進まない。理由が読め、そこから予約へ行けること。
    await expect(page.getByText(`期限内に着ける便がありません（${product}）`))
      .toBeVisible({ timeout: 20_000 });
  });

  test('営業が荷主へ通知し、経路設計へ戻せる（US12・IT6）', async ({ page, request }) => {
    // デモ項目 5・7。**通知は記録だけ**（送信基盤はスコープ外）だが、記録は
    // 業務の守りとして働く——通知していない予約は経路設計へ戻せない。
    const voyageNumber = uniqueVoyageNumber('V-NT-');
    const product = `通知-${Date.now()}`;

    const routingToken = await tokenOf(request, 'routing01');
    const voyage = await request.post('/api/v1/routing/voyages', {
      headers: { Authorization: `Bearer ${routingToken}` },
      data: {
        voyageNumber,
        carrierCode: 'MOL',
        carrierName: '商船三井',
        vesselName: 'NOTIFY EXPRESS',
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USNYC',
            // どの候補より速くする（上の経路確定のテストと同じ理由）。
            departureAt: `${businessDate(2)}T00:00:00Z`,
            arrivalAt: `${businessDate(5)}T00:00:00Z`,
          },
        ],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyage.status()).toBe(201);

    const bookingId = await bookCargo(request, product);
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);
    // **自分が作った航海を名指しで探さない。** 探索は推奨順の上位 20 件で
    // 打ち切る（ADR-0007）ので、同じ区間の航海が 20 本を超えると自分の 1 本が
    // 出るとは限らない。クラスタは作り直さずに使い続けるため、実行のたびに
    // 航海が積み上がる（IT7 の 2 度目の通しで実測。84 本あった）。
    // 経路設計者がするのは「候補を見て 1 つ選ぶ」ことなので、先頭の候補を選ぶ。
    const candidate = page.getByTestId('candidate-1');
    await expect(candidate).toBeVisible({ timeout: 30_000 });
    await candidate.getByRole('radio').check();
    await page.getByRole('button', { name: 'この経路で確定' }).click();
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);

    // 通知内容は旅程から作る。旅程が届く前は送れない。
    await expect(page.getByRole('heading', { name: '荷主への通知' }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel('通知内容')).toHaveValue(/JPTYO → USNYC/, { timeout: 20_000 });
    await page.getByLabel('通知先メールアドレス').fill('shipper@example.com');
    await page.getByRole('button', { name: '通知した記録を残す' }).click();

    // 通知済みになり、履歴に残る（US12 §受入基準 4）。
    await expect(page.getByText('通知済み')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('heading', { name: '通知履歴' })).toBeVisible();

    // 通知したので経路設計へ戻せる（デモ項目 7）。
    await page.getByRole('button', { name: '経路設計へ戻す' }).click();
    await page.getByLabel('戻す理由').fill('荷主が経由港の変更を希望');
    await page.getByRole('button', { name: '戻すことを確定する' }).click();

    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });
    // **旅程は残る。** 消すと「何を組み直すのか」が分からなくなる。
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible();
  });

  test('確定して追跡番号を発行すると、追跡がサービスをまたいで作られる（US13・US14・IT7）',
    async ({ page, request }) => {
      // **本 IT の中核。** サービスをまたぐ最初の連鎖で、Testcontainers では
      // 両サービスを同時に起こさないので**ここでしか通しで確かめられない**。
      const product = `連鎖の貨物-${Date.now()}`;
      const voyageNumber = uniqueVoyageNumber('V-CH-');
      const routing = await tokenOf(request, 'routing01');
      const voyage = await request.post('/api/v1/routing/voyages', {
        headers: { Authorization: `Bearer ${routing}` },
        data: {
          voyageNumber,
          carrierCode: 'MOL',
          carrierName: '商船三井',
          vesselName: 'CHAIN MARU',
          movements: [
            {
              departureUnLocode: 'JPTYO',
              arrivalUnLocode: 'USNYC',
              // どの候補より速くする（上の経路確定のテストと同じ理由）。
              departureAt: `${businessDate(2)}T00:00:00Z`,
              arrivalAt: `${businessDate(5)}T00:00:00Z`,
            },
          ],
          acceptedCargoTypes: ['GENERAL'],
        },
      });
      expect(voyage.status()).toBe(201);

      const bookingId = await bookCargo(request, product);
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '経路設計を依頼する' }).click();
      await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });

      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/routing/bookings/${bookingId}`);
      // 先頭の候補を選ぶ（理由は上と同じ。ADR-0007 の打ち切り）。
      const candidate = page.getByTestId('candidate-1');
      await expect(candidate).toBeVisible({ timeout: 30_000 });
      await candidate.getByRole('radio').check();
      await page.getByRole('button', { name: 'この経路で確定' }).click();
      await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });

      // **経路設計者には確定の操作が出ない**（確定は営業の仕事）。
      await expect(page.getByRole('button', { name: '予約を確定する' })).toHaveCount(0);

      await page.goto('/logout');
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await expect(page.getByLabel('通知内容')).toHaveValue(/JPTYO → USNYC/, { timeout: 20_000 });
      await page.getByLabel('通知先メールアドレス').fill('shipper@example.com');
      await page.getByRole('button', { name: '通知した記録を残す' }).click();
      await expect(page.getByText('通知済み')).toBeVisible({ timeout: 20_000 });

      // デモ項目 1: 通知済みの予約を確定できる。
      await page.getByRole('button', { name: '予約を確定する' }).click();
      // **「確定」は他の文言にも含まれる**（「この経路で確定」など）ので、
      // 状態の欄そのものを見る。
      await expect(page.getByText('確定', { exact: true })).toBeVisible({ timeout: 20_000 });
      // **営業には発行の操作が出ない**（発行は経路設計者の仕事）。
      await expect(page.getByRole('button', { name: '追跡番号を発行する' })).toHaveCount(0);
      // デモ項目 7: 確定した予約は経路設計へ戻せない。
      await expect(page.getByRole('button', { name: '経路設計へ戻す' })).toHaveCount(0);

      // デモ項目 3: 経路設計者が追跡番号を発行できる。
      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '追跡番号を発行する' }).click();
      await expect(page.getByText(/^T-\d{4}-\d{6}$/)).toBeVisible({ timeout: 20_000 });
      // デモ項目 4: 二重に発行されない（操作そのものが消える）。
      await expect(page.getByRole('button', { name: '追跡番号を発行する' })).toHaveCount(0);

      // デモ項目 5: **サービスをまたいで届く。** API を直接叩いても二度目は断られ、
      // 断った理由が読める（器だけの文言に化けない）。
      const second = await request.post(
        `/api/v1/booking/bookings/${bookingId}/tracking-number`,
        { headers: { Authorization: `Bearer ${routing}` }, failOnStatusCode: false });
      expect(second.status()).toBe(409);
      const body = await second.json();
      expect(body.message).toContain('発行できません');
      expect(body.message).not.toContain('com.example.cargotracker');
    });

  test('管理者は利用者の状態を見てロックを解除できる', async ({ page, request }) => {
    // 先に API で 5 回失敗させてロックする。画面から 5 回打つと、E2E が
    // 「ロックの再現手順」ではなく「入力の反復」を測ることになる。
    for (let i = 0; i < 5; i++) {
      await request.post('/api/v1/auth/login', {
        data: { username: 'handler01', password: 'wrong-password' },
        failOnStatusCode: false,
      });
    }

    await signIn(page, 'admin01');
    await page.goto('/admin/users');

    const row = page.locator('tr', { hasText: 'handler01' });
    await expect(row.getByText(/ロック中（あと \d+ 分）/)).toBeVisible();

    await row.getByRole('button', { name: '解除する' }).click();
    await expect(row.getByText(/ロック中/)).toHaveCount(0);

    // 解除できたことを、画面の見え方ではなく実際のログインで確かめる。
    const response = await request.post('/api/v1/auth/login', {
      data: { username: 'handler01', password: 'secret1234' },
      failOnStatusCode: false,
    });
    expect(response.status()).toBe(200);
  });

  test('未認証でもポータルから公開追跡へ入れる', async ({ page }) => {
    await page.goto('/portal');

    await page.getByLabel('追跡番号').fill('ABC12345');
    await page.getByRole('button', { name: '照会する' }).click();

    await expect(page.getByRole('heading', { name: '荷物の追跡' })).toBeVisible();
    await expect(page.getByText('ABC12345')).toBeVisible();
  });
  test('経路設計者が航海を更新すると、差分を確かめて反映できる（US25・IT4）', async ({
    page,
  }) => {
    const voyageNumber = uniqueVoyageNumber('V-UPD-');

    await signIn(page, 'routing01');
    await page.goto('/voyages/new');
    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('MOL');
    await page.getByLabel('運送会社名', { exact: true }).fill('商船三井');
    await page.getByLabel('船名').fill('UPDATE EXPRESS');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('USNYC');
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(45)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧の航海番号から詳細へ入る（IT3 レビューで欠けていた導線）。
    await expect(page.getByRole('link', { name: voyageNumber })).toBeVisible({
      timeout: 20_000,
    });
    await page.getByRole('link', { name: voyageNumber }).click();
    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` })).toBeVisible();
    await expect(page.getByTestId('movement-1')).toContainText('JPTYO → USNYC');

    await page.getByRole('link', { name: '更新する' }).click();
    await expect(page.getByLabel('船名')).toHaveValue('UPDATE EXPRESS', { timeout: 20_000 });
    await page.getByLabel('船名').fill('UPDATE VOYAGER');
    await page.getByRole('button', { name: '差分を確認する' }).click();

    // 差分はサーバが出す。画面で並べていないことは、ここに出る文で分かる。
    await expect(page.getByText('UPDATE EXPRESS → UPDATE VOYAGER')).toBeVisible();
    await page.getByRole('button', { name: '更新する' }).click();

    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` })).toBeVisible();
    await expect(page.getByText('UPDATE VOYAGER')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/最終更新/)).toBeVisible();
  });

  test('経路設計者が条件で航海を絞り込める（US07・IT4）', async ({ page }) => {
    const voyageNumber = uniqueVoyageNumber('V-SRC-');

    await signIn(page, 'routing01');
    await page.goto('/voyages/new');
    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('ONE');
    await page.getByLabel('運送会社名', { exact: true }).fill('オーシャンネットワーク');
    await page.getByLabel('船名').fill('SEARCH HARMONY');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('SGSIN');
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(40)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();
    await expect(page.getByRole('link', { name: voyageNumber })).toBeVisible({
      timeout: 20_000,
    });

    // 条件に合う便だけが残る。
    await page.getByLabel('目的地').fill('SGSIN');
    await page.getByRole('button', { name: '絞り込む' }).click();
    await expect(page.getByRole('link', { name: voyageNumber })).toBeVisible();

    // 合わない条件では 0 件の案内が出て、条件を消して戻れる。
    await page.getByLabel('目的地').fill('BRRIO');
    await page.getByRole('button', { name: '絞り込む' }).click();
    await expect(page.getByText('条件に合う航海はありません')).toBeVisible();

    await page.getByRole('button', { name: '条件を消して探し直す' }).click();
    await expect(page.getByRole('link', { name: voyageNumber })).toBeVisible();
  });

  test('営業が仮受付の予約を修正できる（US32・IT4）', async ({ page }) => {
    const stamp = Date.now();
    const email = `update-${stamp}@example.com`;
    const product = `修正前の貨物-${stamp}`;

    await signIn(page, 'sales01');
    await page.goto('/shippers/new');
    await page.getByLabel('名称').fill(`修正商事 ${stamp}`);
    await page.getByLabel('メールアドレス').fill(email);
    await page.getByLabel('電話番号').fill('03-0000-0000');
    await page.getByLabel('住所').fill('東京都中央区');
    await page.getByRole('button', { name: '登録する' }).click();
    await expect(page.getByText(email)).toBeVisible({ timeout: 20_000 });

    await page.goto('/bookings/new');
    const option = page.locator('#shipperId option', { hasText: `修正商事 ${stamp}` });
    await expect(option).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主').selectOption((await option.getAttribute('value')) ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('USNYC');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('品名').fill(product);
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
    // 一覧の先頭を押さない。前の回の予約が並んでいて、別の予約を開いてしまう。
    await page
      .locator('tr', { hasText: product })
      .getByRole('link', { name: /B-/ })
      .click();
    await expect(page.getByRole('link', { name: '修正する' })).toBeVisible({ timeout: 20_000 });
    await page.getByRole('link', { name: '修正する' }).click();

    await expect(page.getByLabel('品名')).toHaveValue(product, { timeout: 20_000 });
    await page.getByLabel('品名').fill(`${product}（訂正）`);
    await page.getByRole('button', { name: '修正する' }).click();

    // 「貨物」欄の値を見る。IT5 で修正履歴の表が付き、同じ文字列が
    // 「変更後」の欄にも出るようになった（US32 §受入基準 4 の読み口）。
    // 画面のどこかに出ていることだけを見ると、どちらを確かめたのか分からない。
    await expect(page.getByRole('definition').filter({ hasText: `${product}（訂正）` }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/最終更新/)).toBeVisible();

    // 何を変えたかが読める（US32 §受入基準 4・IT5 R.2）。記録だけでは誰にも見えない。
    await expect(page.getByRole('heading', { name: '修正履歴' })).toBeVisible();
    await expect(page.getByTestId('revision-品名')).toContainText(product);

    // 引き渡すと修正の導線が消える（US32 §受入基準 1）。
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    await expect(page.getByText('経路提案中')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('link', { name: '修正する' })).toHaveCount(0);
  });
});
