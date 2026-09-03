import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AttentionListPage } from './AttentionListPage';

function respond(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })));
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
});
