import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { BookingList } from './BookingList';
import { BOOKING_STATUSES, statusLabel } from '../status';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const QUERY = `${API_BASE}/query/graphql`;
const row = (over: object = {}) => ({ id: 'B1', number: 'BK-000001', shipperId: 'S1', status: 'RECEIVED', origin: 'JPTYO', destination: 'NLRTM', deadline: '2027-01-15', version: 1, availableCommands: [], ...over });

describe('SC-25 予約一覧', () => {
  it('予約の番号・状態（日本語）・区間・期限を一覧し、行から詳細へ行ける', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { bookings: [row(), row({ id: 'B2', number: 'BK-000002', status: 'CANCELLED' })] } })));
    renderApp(<BookingList />);
    const table = await screen.findByRole('table');
    expect(within(table).getByText('BK-000001')).toBeInTheDocument();
    expect(within(table).getByText('仮受付')).toBeInTheDocument();
    expect(within(table).getByText('キャンセル')).toBeInTheDocument();
    expect(within(table).getAllByText('JPTYO → NLRTM')).toHaveLength(2);
    expect(within(table).getByRole('link', { name: 'BK-000001' })).toHaveAttribute('href', '/bookings/B1');
  });

  it('1 件も無いときは、何も無いことを示す（空の表を出さない）', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { bookings: [] } })));
    renderApp(<BookingList />);
    expect(await screen.findByText('該当する予約はありません')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('状態で絞り込める。絞り込みは URL に残る（作業入口の件数のリンクと一覧が一致する）', async () => {
    const statuses: (string | null)[] = [];
    server.use(http.post(QUERY, async ({ request }) => {
      const { variables } = (await request.json()) as { variables: { status?: string } };
      statuses.push(variables.status ?? null);
      return HttpResponse.json({ data: { bookings: [row()] } });
    }));
    renderApp(<BookingList />, { route: '/bookings?status=RECEIVED' });
    await screen.findByRole('table');
    expect(statuses.at(-1)).toBe('RECEIVED');
    expect(screen.getByLabelText('状態')).toHaveValue('RECEIVED');
    await userEvent.setup().selectOptions(screen.getByLabelText('状態'), 'CONFIRMED');
    await waitFor(() => expect(statuses.at(-1)).toBe('CONFIRMED'));
  });

  it('権限が無いときは、その旨を示す', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: null, errors: [{ message: 'x', extensions: { code: 'FORBIDDEN' } }] })));
    renderApp(<BookingList />, { role: 'Accountant' });
    expect(await screen.findByRole('alert')).toHaveTextContent('この操作を行う権限がありません');
  });
});

describe('予約状態の日本語表示', () => {
  it('8 つの状態すべてに表示名がある（表に載っていない状態は失敗にする）', () => {
    expect(BOOKING_STATUSES).toHaveLength(8);
    for (const s of BOOKING_STATUSES) expect(statusLabel(s), s).not.toBe(s);
  });

  it('未知の状態は、そのまま表示せず「不明」として示す', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('不明な状態');
  });
});
