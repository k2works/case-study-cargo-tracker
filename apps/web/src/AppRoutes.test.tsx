import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { ROLES, type Role } from '@/providers/AuthProvider';
import { ROUTE_ACCESS, navFor } from '@/features/auth/access';
import { API_BASE, renderApp } from '@/testing/render';
import { server } from '@/testing/server';
import { AppRoutes } from './AppRoutes';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const QUERY = `${API_BASE}/query/graphql`;
const empty = () => http.post(QUERY, async ({ request }) => {
  const { query } = (await request.json()) as { query: string };
  if (query.includes('shippers(')) return HttpResponse.json({ data: { shippers: [] } });
  if (query.includes('bookings(')) return HttpResponse.json({ data: { bookings: [] } });
  return HttpResponse.json({ data: null, errors: [{ message: 'NOT_FOUND', extensions: { code: 'NOT_FOUND' } }] });
});

/** 具体的なパス（パラメータを埋めたもの）。表に画面を足したら、ここにも足さないと下の検査が失敗する。 */
const PATHS: Record<string, string> = {
  '/': '/', '/shippers': '/shippers', '/shippers/new': '/shippers/new', '/shippers/:id': '/shippers/S1',
  '/bookings': '/bookings', '/bookings/new': '/bookings/new', '/bookings/:id': '/bookings/B1',
};

describe('ロール × 画面（F5・F6：到達性と 403。表から全組み合わせを生成する）', () => {
  it('表のすべての画面に、検査用のパスがある（載せ忘れが素通りしない）', () => {
    expect(Object.keys(PATHS).sort()).toEqual(ROUTE_ACCESS.map((r) => r.path).sort());
  });

  for (const route of ROUTE_ACCESS) {
    for (const role of ROLES) {
      const allowed = route.roles.includes(role);
      it(`${route.path} を ${role} が開くと、${allowed ? '画面が出る' : '403 の画面になる'}`, async () => {
        server.use(empty());
        renderApp(<AppRoutes />, { role, route: PATHS[route.path]! });
        if (allowed) {
          expect(await screen.findByRole('heading', { level: 1 })).not.toHaveTextContent('権限がありません');
          expect(screen.queryByText('この画面を開く権限がありません')).not.toBeInTheDocument();
        } else {
          expect(await screen.findByText('この画面を開く権限がありません')).toBeInTheDocument();
        }
      });
    }
  }

  it('ログインしていなければ、ログインが必要なことを示す', async () => {
    renderApp(<AppRoutes />, { role: null, route: '/bookings' });
    expect(await screen.findByText('ログインが必要です')).toBeInTheDocument();
  });
});

describe('ナビゲーション：そのロールが開ける画面だけが、メニューに出る', () => {
  for (const role of ROLES) {
    it(`${role} のメニューは、表で許された画面だけ`, async () => {
      server.use(empty());
      renderApp(<AppRoutes />, { role, route: '/' });
      const nav = await screen.findByRole('navigation', { name: 'メインメニュー' });
      const shown = within(nav).getAllByRole('link').map((a) => a.getAttribute('href'));
      expect(shown).toEqual(navFor(role).map((n) => n.to));
      for (const item of navFor(role)) {
        const entry = ROUTE_ACCESS.find((r) => r.path === item.to);
        expect(entry?.roles.includes(role as Role), `${role}: ${item.to}`).toBe(true);
      }
    });
  }
});

describe('作業入口（ホーム）：件数は、絞り込み済みの一覧へのリンク', () => {
  it('営業担当者のホームに、仮受付の件数が出て、同じ絞り込みの一覧へ行ける。0 件でも行を残す', async () => {
    server.use(http.post(QUERY, () => HttpResponse.json({ data: { bookings: [{ id: 'B1', number: 'BK-1', shipperId: 'S1', status: 'RECEIVED', origin: 'JPTYO', destination: 'NLRTM', deadline: '2027-01-15', version: 1, availableCommands: [] }] } })));
    renderApp(<AppRoutes />, { role: 'Sales', route: '/' });
    const link = await screen.findByRole('link', { name: /仮受付の予約/ });
    await waitFor(() => expect(link).toHaveTextContent('1 件'));
    expect(link).toHaveAttribute('href', '/bookings?status=RECEIVED');
  });

  it('0 件でも行は残る', async () => {
    server.use(empty());
    renderApp(<AppRoutes />, { role: 'Sales', route: '/' });
    const link = await screen.findByRole('link', { name: /仮受付の予約/ });
    await waitFor(() => expect(link).toHaveTextContent('0 件'));
  });

  it('担当の画面がまだ無いロールには、その旨を示す（後の Bolt）', async () => {
    renderApp(<AppRoutes />, { role: 'Accountant', route: '/' });
    expect(await screen.findByText(/後の Bolt で提供します/)).toBeInTheDocument();
  });
});

describe('一覧から登録の画面へ行ける（導線。画面があっても辿れなければ使えない）', () => {
  it.each([
    ['/shippers', '荷主を登録', '/shippers/new'],
    ['/bookings', '予約を登録', '/bookings/new'],
  ])('%s の一覧に「%s」の導線がある', async (route, name, href) => {
    server.use(empty());
    renderApp(<AppRoutes />, { role: 'Sales', route });
    expect(await screen.findByRole('link', { name })).toHaveAttribute('href', href);
  });

  it('登録の権限が無いロールには、予約一覧に「予約を登録」を出さない（押して 403 にしない）', async () => {
    server.use(empty());
    renderApp(<AppRoutes />, { role: 'RouteDesigner', route: '/bookings' });
    expect(await screen.findByRole('heading', { name: '予約' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '予約を登録' })).not.toBeInTheDocument();
  });
});
