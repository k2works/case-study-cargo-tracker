import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { BookingDetail } from './BookingDetail';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const QUERY = `${API_BASE}/query/graphql`;
const booking = {
  id: 'B1', number: 'BK-000001', shipperId: 'S1', status: 'RECEIVED', version: 1, availableCommands: [] as string[],
  origin: 'JPTYO', destination: 'NLRTM', deadline: '2027-01-15', preferredHandover: '2026-12-01',
  cargo: { itemName: '精密機器', weightKg: '120.500', quantity: 3 },
  history: [{ eventId: 'E1', kind: 'REGISTERED', occurredAt: '2026-11-04T01:00:00Z', actorUserId: '01ARZ3NDEKTSV4RRFFQ69G5FAV' }],
};

describe('SC-27 予約詳細', () => {
  it('予約の内容（状態・区間・貨物）と、履歴（種類・日時・実行者）を示す', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { booking } })));
    renderApp(<BookingDetail bookingId="B1" />);
    expect(await screen.findByRole('heading', { name: /BK-000001/ })).toBeInTheDocument();
    expect(screen.getByText('仮受付')).toBeInTheDocument();
    expect(screen.getByText('JPTYO → NLRTM')).toBeInTheDocument();
    expect(screen.getByText('精密機器')).toBeInTheDocument();
    const history = screen.getByRole('table', { name: '履歴' });
    expect(within(history).getByText('登録')).toBeInTheDocument();
    expect(within(history).getByText('01ARZ3NDEKTSV4RRFFQ69G5FAV')).toBeInTheDocument();
  });

  it('実行できる操作が無い状態では、その旨を示す（画面で状態を再判定しない：サーバーの値をそのまま使う）', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { booking } })));
    renderApp(<BookingDetail bookingId="B1" />);
    expect(await screen.findByText('現在、実行できる操作はありません')).toBeInTheDocument();
  });

  it('存在しない予約は、見つからないことを示す', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: null, errors: [{ message: 'NOT_FOUND', extensions: { code: 'NOT_FOUND' } }] })));
    renderApp(<BookingDetail bookingId="X" />);
    expect(await screen.findByRole('alert')).toHaveTextContent('対象が見つかりません');
  });
});
