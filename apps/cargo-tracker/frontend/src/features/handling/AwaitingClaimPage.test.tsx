import { render, screen, waitFor } from '@testing-library/react';
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
          <Route path="/handling/claim" element={<h1>引取の記録</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

function awaiting(over: Record<string, unknown> = {}) {
  return {
    trackingNumber: 'TRK-8K2QX7M4RB',
    bookingId: 'b-1',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    cargoType: 'GENERAL',
    customsStatus: 'CLEARED',
    customsStatusLabel: '通関済',
    declarationNumber: 'IMP-2026-0001',
    claimable: true,
    ...over,
  };
}

const awaitingCargo = awaiting();

describe('引取待ち（H.8 / US16）', () => {
  it('港を選ぶまでは、選ぶよう促す', async () => {
    respondByUrl({ '/handling/voyages': { items: [] } });

    renderAt();

    expect(await screen.findByText(/港を選んでください/)).toBeInTheDocument();
  });

  it('港を選ぶと、引取を待っている貨物が出る', async () => {
    respondByUrl({
      '/awaiting-claim': {
        items: [awaiting()],
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

  it('行から引取を記録しに行ける（追跡番号を書き写させない）', async () => {
    // **窓口で荷受人を待たせたまま、航海を思い出して選び直させない。**
    respondByUrl({
      '/awaiting-claim': {
        items: [awaiting()],
      },
      '/handling/voyages': { items: [{ voyageNumber: 'V-ONE-002', unLocode: 'USNYC', cargoCount: 3 }] },
    });

    renderAt();
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    const link = await screen.findByRole('link', { name: '引取を記録' });
    expect(link).toHaveAttribute(
      'href', '/handling/claim?unLocode=USNYC&trackingNumber=TRK-8K2QX7M4RB');
  });

  it('通関が済んでいないと引取を記録できないことを先に言う（US29 §3）', async () => {
    // IT11 までは「この画面では分かりません。荷主に確かめてください」だった。
    // **US29 で読めるようになった**ので、確かめる先を画面の中に出す。
    respondByUrl({ '/handling/voyages': { items: [] } });

    renderAt();

    expect(await screen.findByText(/通関が済んでいない貨物は引取を記録できません/))
      .toBeInTheDocument();
  });

  it('行に通関状態が出て、渡せないものは引取の入口を出さない', async () => {
    // **窓口に呼び出してから断るのがいちばんまずい**（IT12 レビュー 高）。
    // 一覧で先に分かるようにし、押せるのに断られる入口は出さない。
    respondByUrl({
      '/handling/voyages': { items: [{ voyageNumber: 'V-MOL-001', unLocode: 'USNYC',
        cargoCount: 2 }] },
      '/handling/awaiting-claim': {
        items: [
          awaiting({ trackingNumber: 'TRK-PENDING1234', customsStatus: 'PENDING',
            customsStatusLabel: '審査中', claimable: false }),
          awaiting(),
        ],
      },
    });

    renderAt();
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    expect(await screen.findByText('審査中')).toBeInTheDocument();
    expect(screen.getByText('渡せません')).toBeInTheDocument();
    // 通関済の行だけが引取の入口を持つ。
    expect(screen.getAllByRole('link', { name: '引取を記録' })).toHaveLength(1);
  });

  it('申告が無ければ「申告なし」と出す（「無い」と「審査中」は違う）', async () => {
    respondByUrl({
      '/handling/voyages': { items: [{ voyageNumber: 'V-MOL-001', unLocode: 'USNYC',
        cargoCount: 1 }] },
      '/handling/awaiting-claim': {
        items: [awaiting({ customsStatus: null, customsStatusLabel: null,
          declarationNumber: null, claimable: false })],
      },
    });

    renderAt();
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    expect(await screen.findByText('申告なし')).toBeInTheDocument();
  });

  it('「通関済のものだけ表示」を入れるとサーバに絞りを渡す', async () => {
    const fetchSpy = respondByUrl({
      '/handling/voyages': { items: [{ voyageNumber: 'V-MOL-001', unLocode: 'USNYC',
        cargoCount: 1 }] },
      '/handling/awaiting-claim': { items: [awaiting()] },
    });

    renderAt();
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');
    await userEvent.click(screen.getByRole('checkbox', { name: '通関済のものだけ表示' }));

    // **判定はサーバが持つ。** 画面で絞ると判定が 2 か所になる。
    await waitFor(() => expect(fetchSpy.mock.calls.some((call) =>
      String(call[0]).includes('clearedOnly=true'))).toBe(true));
  });

  it('行から通関申告一覧へ、その追跡番号で絞って行ける', async () => {
    respondByUrl({
      '/handling/voyages': { items: [{ voyageNumber: 'V-MOL-001', unLocode: 'USNYC',
        cargoCount: 1 }] },
      '/handling/awaiting-claim': { items: [awaitingCargo] },
    });

    renderAt();
    await screen.findByRole('option', { name: 'USNYC' });
    await userEvent.selectOptions(screen.getByLabelText('港'), 'USNYC');

    expect(await screen.findByRole('link', { name: '通関を確かめる' }))
      .toHaveAttribute('href', '/customs?trackingNumber=TRK-8K2QX7M4RB');
  });
});
