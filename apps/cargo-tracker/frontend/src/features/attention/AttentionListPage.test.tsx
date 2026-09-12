import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AttentionListPage } from './AttentionListPage';

/**
 * 要確認は booking と routing の 2 か所から取る。**同じ本文を両方に返すと
 * 重複して数が合わなくなる**ので、既定では booking にだけ本文を、routing には
 * 空を返す。routing 側を見るテストは `respondPerService` を使う。
 */
function respond(status: number, body: unknown) {
  respondPerService({ booking: [status, body], routing: [200, { items: [] }] });
}

type ServiceResponse = readonly [number, unknown];

/**
 * サービスごとに応答を変える。
 *
 * <p><b>サービスを増やしたらここにも足す。</b> 足し忘れると、新しいサービスへの
 * 問い合わせが別のサービスの応答を受け取り、同じ項目が二重に出る（IT13 で
 * billingms を足したときに実際に赤くなった）。</p>
 */
function respondPerService(bodies: {
  booking: ServiceResponse;
  routing: ServiceResponse;
  billing?: ServiceResponse;
}) {
  const billing: ServiceResponse = bodies.billing ?? [200, { items: [] }];
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      const [status, body] = url.includes('/routing/')
        ? bodies.routing
        : url.includes('/billing/')
          ? billing
          : bodies.booking;
      return Promise.resolve(new Response(JSON.stringify(body), { status }));
    }),
  );
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AttentionListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('S70 要確認一覧', () => {
  it('弾かれた登録が理由つきで出る', async () => {
    respond(200, {
      items: [
        {
          itemId: 'i-1',
          kind: 'PROJECTION_REJECTED',
          targetType: 'SHIPPER',
          targetId: 'shipper-1',
          assignedRole: 'ROLE_SALES',
          reason: 'メールアドレスの重複',
          occurredAt: '2026-09-03T09:00:00Z',
        },
      ],
    });

    renderPage();

    expect(await screen.findByText('メールアドレスの重複')).toBeInTheDocument();
    expect(screen.getByText('shipper-1')).toBeInTheDocument();
  });

  it('気づくだけで終わらせず、次の行動への導線がある', async () => {
    respond(200, {
      items: [
        {
          itemId: 'i-1',
          kind: 'PROJECTION_REJECTED',
          targetType: 'SHIPPER',
          targetId: 'shipper-1',
          assignedRole: 'ROLE_SALES',
          reason: 'メールアドレスの重複',
          occurredAt: '2026-09-03T09:00:00Z',
        },
      ],
    });

    renderPage();

    expect(await screen.findByRole('link', { name: '修正して再登録する' })).toBeInTheDocument();
  });

  it('重複相手が分かるなら既存の荷主へ行ける', async () => {
    // 重複なのだから、多くの場合は既存の荷主を使えば済む。再入力させる前に
    // その道を出す（ui_design.md S70 の [既存の荷主を見る]）。
    respond(200, {
      items: [
        {
          itemId: 'i-1',
          kind: 'PROJECTION_REJECTED',
          targetType: 'SHIPPER',
          targetId: 'shipper-1',
          assignedRole: 'ROLE_SALES',
          reason: 'メールアドレスの重複',
          relatedShipperId: 'shipper-existing',
          occurredAt: '2026-09-03T09:00:00Z',
        },
      ],
    });

    renderPage();

    expect(await screen.findByRole('link', { name: '既存の荷主を見る' })).toBeInTheDocument();
  });

  it('重複相手が引けないときは出さない', async () => {
    // 押しても何も無い導線を出すと、押した人が状態を読み違える。
    respond(200, {
      items: [
        {
          itemId: 'i-1',
          kind: 'PROJECTION_REJECTED',
          targetType: 'SHIPPER',
          targetId: 'shipper-1',
          assignedRole: 'ROLE_SALES',
          reason: 'メールアドレスの重複',
          relatedShipperId: null,
          occurredAt: '2026-09-03T09:00:00Z',
        },
      ],
    });

    renderPage();

    await screen.findByText('メールアドレスの重複');
    expect(screen.queryByRole('link', { name: '既存の荷主を見る' })).not.toBeInTheDocument();
  });

  it('空のフォームが開く理由を書く', async () => {
    // 黙って空だと「消えた」と受け取られる。個人情報を消えない場所へ写して
    // いないことを言う（ADR-0003）。
    respond(200, {
      items: [
        {
          itemId: 'i-1',
          kind: 'PROJECTION_REJECTED',
          targetType: 'SHIPPER',
          targetId: 'shipper-1',
          assignedRole: 'ROLE_SALES',
          reason: 'メールアドレスの重複',
          relatedShipperId: null,
          occurredAt: '2026-09-03T09:00:00Z',
        },
      ],
    });

    renderPage();

    expect(await screen.findByText(/お手元の資料をご用意ください/)).toBeInTheDocument();
  });

  it('何も無いときはその旨を出す（空の表を出さない）', async () => {
    respond(200, { items: [] });

    renderPage();

    expect(await screen.findByText('確認が必要なものはありません')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
  it('経路設計の要確認も同じ一覧に出る', async () => {
    // 記録するサービスが増えたら、読み口にも足さないと誰にも見えない。
    // routingms は IT3 の途中まで attention_item に書くだけで読み口が無かった。
    respondPerService({
      booking: [200, { items: [] }],
      routing: [
        200,
        {
          items: [
            {
              itemId: 'i-9',
              kind: 'PROJECTION_REJECTED',
              targetType: 'VOYAGE',
              targetId: 'V-0001',
              assignedRole: 'ROLE_ROUTING',
              reason: '航海番号の重複',
              relatedShipperId: null,
              occurredAt: '2026-09-04T09:00:00Z',
            },
          ],
        },
      ],
    });

    renderPage();

    expect(await screen.findByText('航海番号の重複')).toBeInTheDocument();
    expect(screen.getByText('V-0001')).toBeInTheDocument();
  });
});

describe('S70 要確認一覧の「次の行動」（IT7 クローズ）', () => {
  it('予約の項目からは、その予約詳細へ行ける', async () => {
    // **気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。**
    // 荷主の重複用のリンク（「修正して再登録する」）を予約の項目に出しても、
    // 経路設計者は追跡番号を発行し直せない（IT7 クローズの自己レビュー）。
    respond(200, {
      items: [{
          itemId: 'a-1',
          kind: 'CHAIN_COMPENSATED',
          targetType: 'BOOKING',
          targetId: 'b-1',
          assignedRole: 'ROLE_ROUTING',
          reason: '追跡の開始が 3 回とも届きませんでした',
          occurredAt: '2026-09-08T02:00:00Z',
          relatedShipperId: null,
      }],
    });

    renderPage();

    const link = await screen.findByRole('link', { name: '予約を開く' });
    expect(link).toHaveAttribute('href', '/bookings/b-1');
    expect(screen.queryByRole('link', { name: '修正して再登録する' })).not.toBeInTheDocument();
  });

  it('請求を作れなかった予約には「請求を作り直す」がある（材料を直したら戻せる）', async () => {
    // **予約を開くだけでは、締めの母集団に戻らない。** 材料を直しても、引取は
    // もう届かないので誰かが戻さなければ落ち続ける（IT13 レビュー user #3）。
    const calls: { url: string; body: string }[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if ((init?.method ?? 'GET') === 'POST') {
          calls.push({ url, body: String(init?.body ?? '') });
          return Promise.resolve(
            new Response(JSON.stringify({ invoiceId: 'INV-20260912-abcd1234' }), { status: 200 }),
          );
        }
        const items = url.includes('/billing/')
          ? [
              {
                itemId: 'i-recalc',
                kind: 'REACTION_FAILED',
                targetType: 'BOOKING',
                targetId: 'B-1',
                assignedRole: 'ROLE_ACCOUNTANT',
                reason: '貨物 TRK-1 の重量が分からないので請求書を作れません',
                occurredAt: '2026-09-03T09:00:00Z',
              },
            ]
          : [];
        return Promise.resolve(new Response(JSON.stringify({ items }), { status: 200 }));
      }),
    );

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '請求を作り直す' }));

    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toContain('/billing/invoices/recalculate');
    expect(JSON.parse(String(calls[0]?.body))).toEqual({ bookingId: 'B-1' });
  });

  it('確認済にすると、その項目が読み出し元のサービスへ送られて一覧から消える', async () => {
    // **どのサービスの項目かを取り違えると、確認が届かないまま消えたように見える。**
    // 請求の項目を booking へ送っても 404 になるだけで、経理の一覧には残り続ける。
    const billingItem = {
      itemId: 'i-billing',
      kind: 'PROJECTION_REJECTED',
      targetType: 'INVOICE',
      targetId: 'inv-1',
      assignedRole: 'ROLE_ACCOUNTANT',
      reason: '重量が分からない',
      occurredAt: '2026-09-03T09:00:00Z',
    };
    const calls: string[] = [];
    let acknowledged = false;
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if ((init?.method ?? 'GET') === 'POST') {
          calls.push(url);
          acknowledged = true;
          return Promise.resolve(
            new Response(
              JSON.stringify({
                itemId: 'i-billing',
                acknowledgedBy: 'acct01',
                acknowledgedAt: '2026-09-03T10:00:00Z',
              }),
              { status: 200 },
            ),
          );
        }
        const items = url.includes('/billing/') && !acknowledged ? [billingItem] : [];
        return Promise.resolve(new Response(JSON.stringify({ items }), { status: 200 }));
      }),
    );

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '確認済にする' }));

    expect(calls).toHaveLength(1);
    expect(calls[0]).toContain('/billing/attention-items/i-billing/acknowledge');
    await waitFor(() =>
      expect(screen.getByText('確認が必要なものはありません')).toBeInTheDocument(),
    );
  });
});
