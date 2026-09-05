import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BookingEditPage } from './BookingEditPage';
import { useAuthStore } from '@/shared/auth/authStore';

function booking(over: Record<string, unknown> = {}) {
  return {
    bookingId: 'b-1',
    bookingNumber: 'B-2026-0903-0001',
    shipperId: 's-1',
    shipperName: '山田商事',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    arrivalDeadline: '2026-12-01',
    cargoType: 'GENERAL',
    weightKg: '1200.00',
    lengthCm: '120.00',
    widthCm: '80.00',
    heightCm: '100.00',
    quantity: 10,
    productName: '自動車部品',
    hazardImoClass: null,
    hazardUnNumber: null,
    temperatureMinC: null,
    temperatureMaxC: null,
    bookingStatus: 'PRELIMINARY',
    routingStatus: 'NOT_ROUTED',
    bookedAt: '2026-09-03T01:00:00Z',
    routingRequestedAt: null,
    updatedAt: null,
    updatedBy: null,
    ...over,
  };
}

function renderEdit() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/bookings/b-1/edit']}>
        <Routes>
          <Route path="/bookings/:bookingId/edit" element={<BookingEditPage />} />
          <Route path="/bookings/:bookingId" element={<h1>予約詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S24 予約修正（US32）', () => {
  it('既登録の内容が入った状態で開く', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 })),
    );

    renderEdit();

    await waitFor(() => expect(screen.getByLabelText('品名')).toHaveValue('自動車部品'));
    expect(screen.getByLabelText('出発地')).toHaveValue('JPTYO');
    expect(screen.getByLabelText('重量 (kg)')).toHaveValue('1200.00');
    // 荷主は変えられない（不変条件 1）。間違えたならそれは別の予約である。
    expect(screen.queryByLabelText('荷主')).not.toBeInTheDocument();
    expect(screen.getByText(/山田商事/)).toBeInTheDocument();
  });

  it('直した内容を PUT で送る', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((_, init) =>
      Promise.resolve(
        (init as RequestInit | undefined)?.method === 'PUT'
          ? new Response(JSON.stringify({ bookingId: 'b-1' }), { status: 200 })
          : new Response(JSON.stringify(booking()), { status: 200 }),
      ),
    );

    renderEdit();
    await waitFor(() => expect(screen.getByLabelText('品名')).toHaveValue('自動車部品'));
    await userEvent.clear(screen.getByLabelText('品名'));
    await userEvent.type(screen.getByLabelText('品名'), '自動車部品（訂正）');
    await userEvent.click(screen.getByRole('button', { name: '修正する' }));

    await waitFor(() => {
      const put = fetchSpy.mock.calls.find(
        ([, init]) => (init as RequestInit | undefined)?.method === 'PUT',
      );
      expect(put).toBeDefined();
      const body = JSON.parse(String((put?.[1] as RequestInit).body));
      expect(body.productName).toBe('自動車部品（訂正）');
      expect(body.originUnLocode).toBe('JPTYO');
      // 送らなかった項目が落ちると、直していない値まで消える。
      expect(body.quantity).toBe(10);
      expect(body.arrivalDeadline).toBe('2026-12-01');
    });
  });

  it('危険物に直すと申告の欄が要る', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(booking()), { status: 200 })),
    );

    renderEdit();
    await waitFor(() => expect(screen.getByLabelText('品名')).toHaveValue('自動車部品'));

    await userEvent.click(screen.getByLabelText('危険物'));

    expect(screen.getByLabelText('IMO クラス')).toBeRequired();
    expect(screen.getByLabelText('UN 番号')).toBeRequired();
  });

  it('危険物の予約は申告が入った状態で開く', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify(
            booking({ cargoType: 'HAZARDOUS', hazardImoClass: '3', hazardUnNumber: 'UN1263' }),
          ),
          { status: 200 },
        ),
      ),
    );

    renderEdit();

    await waitFor(() => expect(screen.getByLabelText('IMO クラス')).toHaveValue('3'));
    expect(screen.getByLabelText('UN 番号')).toHaveValue('UN1263');
  });

  it('断られた理由をそのまま見せる', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((_, init) =>
      Promise.resolve(
        (init as RequestInit | undefined)?.method === 'PUT'
          ? new Response(
              JSON.stringify({
                code: 'ILLEGAL_STATE',
                message: '状態 ROUTE_PROPOSED の予約は修正できません',
              }),
              { status: 409 },
            )
          : new Response(JSON.stringify(booking()), { status: 200 }),
      ),
    );

    renderEdit();
    await waitFor(() => expect(screen.getByLabelText('品名')).toHaveValue('自動車部品'));
    await userEvent.click(screen.getByRole('button', { name: '修正する' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('修正できません');
  });

  it('修正できない状態の予約は編集させない', async () => {
    // 直接 URL を叩いても開けないようにする。押してから 409 で気づくのは遅い。
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify(booking({ bookingStatus: 'ROUTE_PROPOSED' })), {
          status: 200,
        }),
      ),
    );

    renderEdit();

    expect(await screen.findByRole('alert')).toHaveTextContent('修正できません');
    expect(screen.queryByLabelText('品名')).not.toBeInTheDocument();
  });
});
