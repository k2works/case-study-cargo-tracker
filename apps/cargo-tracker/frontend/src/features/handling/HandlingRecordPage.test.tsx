import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HandlingRecordPage } from './HandlingRecordPage';
import { useAuthStore } from '@/shared/auth/authStore';

function cargo(over: Record<string, unknown> = {}) {
  return {
    trackingNumber: 'TRK-8K2QX7M4RB',
    bookingId: 'b-1',
    originUnLocode: 'JPTYO',
    destinationUnLocode: 'USNYC',
    cargoType: 'GENERAL',
    handledHere: false,
    ...over,
  };
}

/** URL で出し分ける。1 つの応答を全部に返すと、経路の取り違えに気づけない。 */
function respondByUrl(handlers: Record<string, unknown>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (init?.method === 'POST') {
      // **POST も URL で出し分ける。** 全部 201 で返すと、送信先を取り違えても緑。
      return url.includes('/handling/activities')
        ? new Response('', { status: 201 })
        : new Response(JSON.stringify({ code: 'NOT_FOUND', message: '見つかりません' }),
          { status: 404 });
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

function renderAt(path = '/handling/voyages/V-MOL-001?unLocode=SGSIN') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/handling/voyages/:voyageNumber" element={<HandlingRecordPage />} />
          <Route path="/handling/:trackingNumber" element={<h1>荷役履歴</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({ user: { username: 'handler01', roles: ['ROLE_HANDLER'], token: 't' } });
});
afterEach(() => vi.restoreAllMocks());

describe('S50 荷役作業記録', () => {
  it('航海と港から、この船で降ろす貨物が出る', async () => {
    respondByUrl({ '/cargos?unLocode': { items: [cargo()] } });

    renderAt();

    // 一覧の行で確かめる（件数の案内にも「未記録」が出るので、行を見る）。
    const row = await screen.findByRole('row', { name: /TRK-8K2QX7M4RB/ });
    expect(row).toHaveTextContent('未記録');
  });

  it('US15 §2: 作業種別を選べる（引取は本 IT で出さない）', async () => {
    respondByUrl({ '/cargos?unLocode': { items: [] } });

    renderAt();

    const select = await screen.findByLabelText('作業種別');
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.textContent);
    expect(options).toContain('受領');
    expect(options).toContain('積込');
    expect(options).toContain('荷降し');
    // 引取は通関と荷受人の確認が要る（US16・IT10）。
    expect(options).not.toContain('引取');
  });

  it('US15 §1: 追跡番号を入れると貨物を確認できる', async () => {
    respondByUrl({
      '/cargos?unLocode': { items: [cargo()] },
      '/handling/cargos/TRK-8K2QX7M4RB': {
        trackingNumber: 'TRK-8K2QX7M4RB',
        bookingId: 'b-1',
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        cargoType: 'GENERAL',
        legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'SGSIN' }],
      },
    });

    renderAt();
    await userEvent.type(await screen.findByLabelText('追跡番号'), 'TRK-8K2QX7M4RB');

    // 確認欄で見る（一覧の行にも同じ区間が出る）。
    expect(await screen.findByText('確認')).toBeInTheDocument();
    expect(screen.getByText('確認').nextElementSibling).toHaveTextContent('JPTYO → USNYC');
  });

  it('US15 §7: 予定ルート外なら記録する前に警告が出る', async () => {
    // **押す前に知らせる。** 押してから警告すると、作業員は取り消しの手間を負う。
    respondByUrl({
      '/cargos?unLocode': { items: [cargo()] },
      '/handling/cargos/TRK-8K2QX7M4RB': {
        trackingNumber: 'TRK-8K2QX7M4RB',
        bookingId: 'b-1',
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        cargoType: 'GENERAL',
        legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'DEHAM' }],
      },
    });

    renderAt();
    await userEvent.type(await screen.findByLabelText('追跡番号'), 'TRK-8K2QX7M4RB');

    expect(await screen.findByText(/予定ルートに含まれていません/)).toBeInTheDocument();
    // 記録は拒まない。
    expect(screen.getByRole('button', { name: '記録する' })).toBeEnabled();
  });

  it('US15 §6: 存在しない追跡番号は知らせる', async () => {
    respondByUrl({ '/cargos?unLocode': { items: [] } });

    renderAt();
    await userEvent.type(await screen.findByLabelText('追跡番号'), 'TRK-NOSUCHNUM');

    expect(await screen.findByText(/この追跡番号の貨物が見つかりません/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '記録する' })).toBeDisabled();
  });

  it('記録すると送信され、貨物だけが空になる（連続記録）', async () => {
    const fetchSpy = respondByUrl({
      '/cargos?unLocode': { items: [cargo()] },
      '/handling/cargos/TRK-8K2QX7M4RB': {
        trackingNumber: 'TRK-8K2QX7M4RB',
        bookingId: 'b-1',
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        cargoType: 'GENERAL',
        legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'SGSIN' }],
      },
    });

    renderAt();
    await userEvent.selectOptions(await screen.findByLabelText('作業種別'), 'UNLOAD');
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-8K2QX7M4RB');
    await screen.findByText('確認');
    await userEvent.click(screen.getByRole('button', { name: '記録する' }));

    await waitFor(() => {
      const posted = fetchSpy.mock.calls.find((call) => call[1]?.method === 'POST');
      expect(String(posted?.[0])).toContain('/handling/activities');
      expect(String(posted?.[1]?.body)).toContain('TRK-8K2QX7M4RB');
      // **キー名の存在だけでは保証にならない。** 値が毎回変わっても緑になる。
      expect(JSON.parse(String(posted?.[1]?.body)).activityId).toMatch(/^[0-9a-f-]{36}$/);
    });

    // 種別と場所は保つ。貨物だけ空にして次の 1 本へ。
    await waitFor(() => expect(screen.getByLabelText('追跡番号')).toHaveValue(''));
    expect(screen.getByLabelText('作業種別')).toHaveValue('UNLOAD');
  });


  it('US15 §3: 起きた日時を後から入れられる（紙に控えて戻ってから入れる）', async () => {
    const fetchSpy = respondByUrl({
      '/cargos?unLocode': { items: [cargo()] },
      '/handling/cargos/TRK-8K2QX7M4RB': {
        trackingNumber: 'TRK-8K2QX7M4RB',
        bookingId: 'b-1',
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        cargoType: 'GENERAL',
        legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'SGSIN' }],
      },
    });

    renderAt();
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-8K2QX7M4RB');
    await screen.findByText('確認');
    await userEvent.type(screen.getByLabelText('作業日時'), '2026-09-16T22:30');
    await userEvent.click(screen.getByRole('button', { name: '記録する' }));

    await waitFor(() => {
      const posted = fetchSpy.mock.calls.find((call) => call[1]?.method === 'POST');
      // 業務タイムゾーンで解釈して送る（ブラウザの時計に依らない）。
      expect(JSON.parse(String(posted?.[1]?.body)).completedAt).toContain('2026-09-16T13:30');
    });
  });

  it('応答が返らずもう一度押しても、同じ鍵で送る（二重記録にしない）', async () => {
    // **現場は電波の届かない岸壁で使う。** 押し直すたびに鍵を作り直すと、
    // サーバから見て別々の作業になり、同じ貨物が二度記録される。
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        // 応答が返らない（通信断）。画面には成功が伝わらない。
        return new Response('', { status: 503 });
      }
      if (url.includes('/handling/cargos/TRK-8K2QX7M4RB')) {
        return new Response(JSON.stringify({
        trackingNumber: 'TRK-8K2QX7M4RB',
        bookingId: 'b-1',
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        cargoType: 'GENERAL',
        legs: [{ voyageNumber: 'V-MOL-001', loadUnLocode: 'JPTYO', unloadUnLocode: 'SGSIN' }],
      }), { status: 200 });
      }
      if (url.includes('/cargos?unLocode')) {
        return new Response(JSON.stringify({ items: [cargo()] }), { status: 200 });
      }
      // 打ちかけの番号（TRK- など）は見つからない。フォールバックで一覧を
      // 返すと、照会の応答が一覧になって画面が壊れる。
      return new Response(JSON.stringify({ code: 'NOT_FOUND', message: '見つかりません' }),
        { status: 404 });
    });

    renderAt();
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-8K2QX7M4RB');
    await screen.findByText('確認');
    await userEvent.click(screen.getByRole('button', { name: '記録する' }));
    await waitFor(() =>
      expect(fetchSpy.mock.calls.filter((call) => call[1]?.method === 'POST')).toHaveLength(1));
    await userEvent.click(screen.getByRole('button', { name: '記録する' }));

    await waitFor(() => {
      const posts = fetchSpy.mock.calls.filter((call) => call[1]?.method === 'POST');
      expect(posts).toHaveLength(2);
      const keys = posts.map((post) => JSON.parse(String(post[1]?.body)).activityId);
      expect(keys[1]).toEqual(keys[0]);
    });
  });

  it('送信済みは「記録済」として積み上がる（取り消しは S51 で行う）', async () => {
    respondByUrl({ '/cargos?unLocode': { items: [cargo({ handledHere: true })] } });

    renderAt();

    const row = await screen.findByRole('row', { name: /TRK-8K2QX7M4RB/ });
    expect(row).toHaveTextContent('記録済');
  });

  it('港が指定されていなければ、選ぶよう促す', async () => {
    // 港が無いと「この船からどこで降ろすか」が決まらない。
    respondByUrl({});

    renderAt('/handling/voyages/V-MOL-001');

    expect(await screen.findByText(/港を選んでください/)).toBeInTheDocument();
  });
});
