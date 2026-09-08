import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AwaitingClaimPage } from './AwaitingClaimPage';
import { useAuthStore } from '@/shared/auth/authStore';

function respondByUrl(handlers: Record<string, unknown>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    for (const [fragment, body] of Object.entries(handlers)) {
      if (url.includes(fragment)) {
        return new Response(JSON.stringify(body), { status: 200 });
      }
    }
    return new Response(JSON.stringify({ code: 'NOT_FOUND', message: '見つかりません' }),
      { status: 404 });
  });
}

function renderAt(path = '/handling/awaiting-claim') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/handling/awaiting-claim" element={<AwaitingClaimPage />} />
          <Route path="/handling/voyages/:voyageNumber" element={<h1>荷役の記録</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('引取待ち（H.8 / US16）', () => {
  it('港を選ぶまでは、選ぶよう促す', async () => {
    respondByUrl({ '/handling/voyages': { items: [] } });

    renderAt();

    expect(await screen.findByText(/港を選んでください/)).toBeInTheDocument();
  });

  it('港を選ぶと、引取を待っている貨物が出る', async () => {
    respondByUrl({
      '/awaiting-claim': {
        items: [{
          trackingNumber: 'TRK-8K2QX7M4RB',
          bookingId: 'b-1',
          originUnLocode: 'JPTYO',
          destinationUnLocode: 'USNYC',
          cargoType: 'GENERAL',
          handledTypes: ['UNLOAD'],
        }],
      },
      '/handling/voyages': { items: [{ voyageNumber: 'V-ONE-002', unLocode: 'USNYC', cargoCount: 3 }] },
    });

    renderAt();
    // 港の選択肢は「作業のある航海」から作る。届くまで待ってから選ぶ。
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    expect(await screen.findByText('TRK-8K2QX7M4RB')).toBeInTheDocument();
  });

  it('引取待ちが無ければ、そう言う（空欄で終わらせない）', async () => {
    respondByUrl({
      '/awaiting-claim': { items: [] },
      '/handling/voyages': { items: [{ voyageNumber: 'V-ONE-002', unLocode: 'USNYC', cargoCount: 3 }] },
    });

    renderAt();
    // 港の選択肢は「作業のある航海」から作る。届くまで待ってから選ぶ。
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    expect(await screen.findByText(/引取を待っている貨物はありません/)).toBeInTheDocument();
  });
});
