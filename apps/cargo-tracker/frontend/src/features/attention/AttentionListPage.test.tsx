import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
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

function respondPerService(bodies: { booking: ServiceResponse; routing: ServiceResponse }) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      const [status, body] = url.includes('/routing/') ? bodies.routing : bodies.booking;
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
});
