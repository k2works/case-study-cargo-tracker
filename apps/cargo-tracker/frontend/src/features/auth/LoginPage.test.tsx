import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
import { useAuthStore } from '@/shared/auth/authStore';

function mockFetch(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })),
  );
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<h1>ダッシュボード</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});
afterEach(() => vi.unstubAllGlobals());

describe('S00 ログイン', () => {
  it('成功するとダッシュボードへ移り、ロールを持つ', async () => {
    mockFetch(200, {
      token: 'jwt-token',
      username: 'sales01',
      displayName: '営業 太郎',
      roles: ['ROLE_SALES'],
      shipperId: null,
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText('利用者名'), 'sales01');
    await userEvent.type(screen.getByLabelText('パスワード'), 'secret1234');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'ダッシュボード' })).toBeInTheDocument(),
    );
    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(true);
  });

  it('失敗すると API の文言をそのまま出し、理由を足さない', async () => {
    mockFetch(401, {
      code: 'SIGN_IN_FAILED',
      message: '利用者名またはパスワードが正しくありません',
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText('利用者名'), 'sales01');
    await userEvent.type(screen.getByLabelText('パスワード'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('利用者名またはパスワードが正しくありません');
    expect(alert.textContent).not.toMatch(/存在しません|ロック/);
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('通信の失敗は業務の拒否と別の文言にする', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')));

    renderLogin();
    await userEvent.type(screen.getByLabelText('利用者名'), 'sales01');
    await userEvent.type(screen.getByLabelText('パスワード'), 'secret1234');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('通信に失敗しました');
  });

  it('知らないロールは捨てる（名簿に無いものを通さない）', async () => {
    mockFetch(200, {
      token: 't',
      username: 'x',
      displayName: 'x',
      roles: ['ROLE_SALES', 'ROLE_UNKNOWN'],
      shipperId: null,
    });

    renderLogin();
    await userEvent.type(screen.getByLabelText('利用者名'), 'x');
    await userEvent.type(screen.getByLabelText('パスワード'), 'y');
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }));

    await waitFor(() => expect(useAuthStore.getState().user).not.toBeNull());
    expect(useAuthStore.getState().user?.roles).toEqual(['ROLE_SALES']);
  });

  it('認証なしで使える追跡照会への導線がある', () => {
    renderLogin();

    expect(screen.getByRole('link', { name: /ログインなしで照会/ })).toBeInTheDocument();
  });
});
