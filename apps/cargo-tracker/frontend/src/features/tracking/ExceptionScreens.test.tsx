import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExceptionListPage } from './ExceptionListPage';
import { ExceptionReportPage } from './ExceptionReportPage';
import { useAuthStore } from '@/shared/auth/authStore';

function exceptionItem(over: Record<string, unknown> = {}) {
  return {
    exceptionId: 'ex-1',
    trackingNumber: 'TRK-8K2QX7M4RB',
    exceptionType: 'DELAY',
    exceptionTypeLabel: '遅延',
    responseStatus: 'REPORTED',
    responseStatusLabel: '起票',
    urgent: false,
    unLocode: 'SGSIN',
    description: '台風で 3 日遅れます',
    occurredAt: '2026-09-20T02:00:00Z',
    estimatedArrival: '2026-09-24',
    transportStatus: 'EXCEPTION',
    transportStatusLabel: '例外発生',
    bookingId: 'B-2026-0902-004',
    escalatedAt: null,
    settled: false,
    ...over,
  };
}

function respondByUrl(handlers: Record<string, unknown>, onPost?: (url: string) => void) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (init?.method === 'POST') {
      onPost?.(url);
      // **204 は本文を持てない。** '' を渡すと Response の生成そのものが投げる。
      return new Response(null, { status: 204 });
    }
    for (const [fragment, body] of Object.entries(handlers)) {
      if (url.includes(fragment)) {
        return new Response(JSON.stringify(body), { status: 200 });
      }
    }
    return new Response(JSON.stringify({ code: 'NOT_FOUND', message: '見つかりません' }),
      { status: 404 });
  });
}

function renderList() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tracking/exceptions']}>
        <Routes>
          <Route path="/tracking/exceptions" element={<ExceptionListPage />} />
          <Route path="/tracking/:trackingNumber" element={<h1>追跡詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderReport() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tracking/TRK-8K2QX7M4RB/exceptions/new']}>
        <Routes>
          <Route
            path="/tracking/:trackingNumber/exceptions/new"
            element={<ExceptionReportPage />}
          />
          <Route path="/tracking/:trackingNumber" element={<h1>追跡詳細</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'tracker01', roles: ['ROLE_TRACKER'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S42 例外一覧（US19 §5）', () => {
  it('未解決の例外が、サーバの並びのまま出る', async () => {
    // **並べ直さない。** 緊急が先、以降は残日数が少ない順（不変条件 7）を
    // サーバが決める。画面で並べ直すと判定が 2 か所になる。
    respondByUrl({
      '/tracking/trackings/exceptions': {
        items: [
          exceptionItem({ exceptionId: 'ex-loss', exceptionTypeLabel: '紛失', urgent: true }),
          exceptionItem({ exceptionId: 'ex-delay' }),
        ],
      },
    });

    renderList();

    const rows = await screen.findAllByRole('row');
    // 1 行目は見出し。
    expect(rows[1]).toHaveTextContent('紛失');
    expect(rows[2]).toHaveTextContent('遅延');
  });

  it('緊急の例外が目で分かる', async () => {
    respondByUrl({
      '/tracking/trackings/exceptions': {
        items: [exceptionItem({ exceptionTypeLabel: '紛失', urgent: true })],
      },
    });

    renderList();

    expect(await screen.findByText('緊急')).toBeInTheDocument();
  });

  it('予約番号が出る（電話は「A 社の予約の件で」から始まる）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();

    expect(await screen.findByText('B-2026-0902-004')).toBeInTheDocument();
  });

  it('上位者へ知らせた時刻が読める（US20 §3）', async () => {
    // **「未連絡」の印はやめた**（IT11 レビュー 高）。起票と同時に知らせるので
    // 通常は点かず、点いても消す操作が無い飾りだった。事実を出す。
    respondByUrl({
      '/tracking/trackings/exceptions': {
        items: [exceptionItem({ urgent: true, escalatedAt: '2026-09-20T03:00:00Z' })],
      },
    });

    renderList();

    expect(await screen.findByText(/上位者へ連絡/)).toBeInTheDocument();
  });

  it('知らせていない例外には連絡の欄を出さない', async () => {
    respondByUrl({
      '/tracking/trackings/exceptions': {
        items: [exceptionItem({ urgent: false, escalatedAt: null })],
      },
    });

    renderList();

    expect(await screen.findByText('遅延')).toBeInTheDocument();
    expect(screen.queryByText(/上位者へ連絡/)).not.toBeInTheDocument();
  });

  it('未解決の行から対応へ入れる（一覧には目的の操作を置く）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();

    expect(await screen.findByRole('link', { name: '対応する' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB');
  });

  it('予約番号から予約詳細へ行ける（経路設計者へ渡す前の状況確認）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();

    expect(await screen.findByRole('link', { name: 'B-2026-0902-004' }))
      .toHaveAttribute('href', '/bookings/B-2026-0902-004');
  });

  it('解決済も表示に切り替えると、解決済を含めて問い合わせる（US28 §8）', async () => {
    const urls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      urls.push(String(input));
      return new Response(JSON.stringify({ items: [exceptionItem()] }), { status: 200 });
    });

    renderList();
    await screen.findByText('B-2026-0902-004');
    await userEvent.click(screen.getByLabelText('解決済も表示する'));

    await vi.waitFor(() => {
      expect(urls.some((url) => url.includes('includeResolved=true'))).toBe(true);
    });
  });

  it('管理者は中身を読めるが、起票と対応の導線は出さない（IT11 レビュー 高）', async () => {
    // **escalation の宛先が一番動けない**のは成り立たない。追跡詳細は開ける。
    // 起票・対応は追跡管理者の仕事なので出さない。
    useAuthStore.setState({
      user: { username: 'admin01', roles: ['ROLE_ADMIN'], token: 't' },
    });
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();

    expect(await screen.findByRole('link', { name: 'TRK-8K2QX7M4RB' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB');
    expect(screen.queryByRole('link', { name: '例外を起票' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '対応する' })).not.toBeInTheDocument();
  });

  it('一覧の行から例外を起票できる（IT10 レビュー N10）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();

    expect(await screen.findByRole('link', { name: '例外を起票' }))
      .toHaveAttribute('href', '/tracking/TRK-8K2QX7M4RB/exceptions/new');
  });

  it('一覧から対象の追跡へ行ける（気づく手段は次の行動へ繋ぐ）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [exceptionItem()] } });

    renderList();
    await userEvent.click(await screen.findByRole('link', { name: 'TRK-8K2QX7M4RB' }));

    expect(await screen.findByRole('heading', { name: '追跡詳細' })).toBeInTheDocument();
  });

  it('未解決が無ければ、そう言う（空欄で終わらせない）', async () => {
    respondByUrl({ '/tracking/trackings/exceptions': { items: [] } });

    renderList();

    expect(await screen.findByText(/未解決の例外はありません/)).toBeInTheDocument();
  });
});

describe('S43 例外起票（US19 §1）', () => {
  it('自動で起票される種別は選べない（起きていない誤配を記録させない）', async () => {
    respondByUrl({});

    renderReport();

    const select = await screen.findByLabelText('例外種別');
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.textContent);
    expect(options).toContain('遅延');
    expect(options).toContain('破損');
    expect(options).toContain('紛失');
    expect(options).not.toContain('誤配');
    expect(options).not.toContain('税関保留');
  });

  it('発生状況が空のままでは起票できない', async () => {
    respondByUrl({});

    renderReport();

    expect(await screen.findByRole('button', { name: '起票する' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('発生状況'), '台風で 3 日遅れます');
    expect(screen.getByRole('button', { name: '起票する' })).toBeEnabled();
  });

  it('起票すると、入力した内容が送られてその追跡の詳細へ戻る', async () => {
    // **URL だけを見ない。** 組み立て（種別・場所・発生日時）を潰しても緑になる。
    const posted: { url: string; body: unknown }[] = [];
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        posted.push({ url, body: JSON.parse(String(init.body)) });
        return new Response(null, { status: 204 });
      }
      return new Response(JSON.stringify({ code: 'NOT_FOUND', message: '見つかりません' }),
        { status: 404 });
    });

    renderReport();
    await userEvent.selectOptions(await screen.findByLabelText('例外種別'), 'DAMAGE');
    await userEvent.type(screen.getByLabelText('発生場所'), 'sgsin');
    // **業務タイムゾーンの時刻として送る**（UTC 直送だと時差の分ずれる）。
    await userEvent.type(screen.getByLabelText('発生日時'), '2026-09-20T11:00');
    await userEvent.type(screen.getByLabelText('発生状況'), '外装が破れています');
    await userEvent.click(screen.getByRole('button', { name: '起票する' }));

    await waitFor(() => expect(posted).toHaveLength(1));
    const sent = posted[0]?.body as Record<string, unknown>;
    expect(posted[0]?.url).toContain('/TRK-8K2QX7M4RB/exceptions');
    expect(sent.exceptionType).toBe('DAMAGE');
    // 港コードは大文字にして送る（入力の揺れを画面が吸収する）。
    expect(sent.unLocode).toBe('SGSIN');
    expect(sent.description).toBe('外装が破れています');
    expect(String(sent.occurredAt)).toBe('2026-09-20T02:00:00Z');
    // **例外 ID はサーバが採番する**（IT10 レビュー N7）。呼ぶ側が決めると、
    // 別の追跡で同じ ID が来たときに投影の insert だけが落ちる。
    expect(sent).not.toHaveProperty('exceptionId');
    expect(await screen.findByRole('heading', { name: '追跡詳細' })).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalled();
  });
});
