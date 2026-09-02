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

  it('何も無いときはその旨を出す（空の表を出さない）', async () => {
    respond(200, { items: [] });

    renderPage();

    expect(await screen.findByText('確認が必要なものはありません')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
