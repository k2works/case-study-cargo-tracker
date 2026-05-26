import { describe, it, expect, vi, beforeEach } from 'vitest';
import { login, logout } from './authApi';

type FetchMock = ReturnType<typeof vi.fn>;

describe('authApi (US00)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('ログインに成功するとトークンとロールを返す', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: true,
      json: async () => ({ token: 'jwt', role: 'ROLE_ADMIN' }),
    });

    const res = await login({ username: 'admin', password: 'password' });

    expect(res).toEqual({ token: 'jwt', role: 'ROLE_ADMIN' });
    expect(fetch).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('ログイン失敗時はサーバーの detail で例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: false,
      json: async () => ({ detail: 'ユーザーが見つかりません' }),
    });

    await expect(login({ username: 'x', password: 'y' })).rejects.toThrow('ユーザーが見つかりません');
  });

  it('ログイン失敗で JSON が壊れていても既定メッセージで例外を投げる', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('bad json');
      },
    });

    await expect(login({ username: 'x', password: 'y' })).rejects.toThrow('ログインに失敗しました');
  });

  it('ログアウトは /api/auth/logout に POST する', async () => {
    (fetch as unknown as FetchMock).mockResolvedValue({ ok: true });

    await logout();

    expect(fetch).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST' });
  });
});
