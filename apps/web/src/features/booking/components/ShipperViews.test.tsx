import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { ShipperDetail } from './ShipperDetail';
import { ShipperList } from './ShipperList';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const QUERY = `${API_BASE}/query/graphql`;

describe('SC-20 荷主一覧・検索', () => {
  it('荷主を一覧し、行から詳細へ行ける。検索語は問い合わせに渡る', async () => {
    const keywords: (string | null)[] = [];
    server.use(http.post(QUERY, async ({ request }) => {
      const { variables } = (await request.json()) as { variables: { keyword?: string } };
      keywords.push(variables.keyword ?? null);
      return HttpResponse.json({ data: { shippers: [{ id: 'S1', name: '山田 太郎', email: 'taro@example.com', kind: 'INDIVIDUAL' }] } });
    }));
    renderApp(<ShipperList />);
    const link = await screen.findByRole('link', { name: '山田 太郎' });
    expect(link).toHaveAttribute('href', '/shippers/S1');
    expect(screen.getByText('個人')).toBeInTheDocument();
    await userEvent.setup().type(screen.getByLabelText('検索（氏名・メール）'), '山田');
    await waitFor(() => expect(keywords.at(-1)).toBe('山田'));
  });

  it('荷主の情報（個人情報）は営業担当者以外には出ない（FORBIDDEN を示す）', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: null, errors: [{ message: 'x', extensions: { code: 'FORBIDDEN' } }] })));
    renderApp(<ShipperList />, { role: 'RouteDesigner' });
    expect(await screen.findByRole('alert')).toHaveTextContent('この操作を行う権限がありません');
  });
});

describe('SC-22 荷主詳細', () => {
  it('この荷主で予約を登録する導線がある（荷主を選び直さずに予約へ進める）', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { shipper: { id: 'S1', kind: 'INDIVIDUAL', name: '山田 太郎', address: '東京都', contact: '03-0000-0000', email: 'taro@example.com' } } })));
    renderApp(<ShipperDetail shipperId="S1" />);
    expect(await screen.findByRole('link', { name: 'この荷主で予約を登録' })).toHaveAttribute('href', '/bookings/new?shipperId=S1');
  });

  it('荷主の情報を示す', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { shipper: { id: 'S1', kind: 'INDIVIDUAL', name: '山田 太郎', address: '東京都千代田区', contact: '03-1234-5678', email: 'taro@example.com' } } })));
    renderApp(<ShipperDetail shipperId="S1" />);
    expect(await screen.findByText('東京都千代田区')).toBeInTheDocument();
    expect(screen.getByText('taro@example.com')).toBeInTheDocument();
  });
});
