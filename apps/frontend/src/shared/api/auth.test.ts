import { describe, it, expect, beforeEach } from 'vitest';
import { authHeader, TOKEN_STORAGE_KEY } from './auth';

describe('authHeader', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('トークンが無ければ空オブジェクトを返す', () => {
    expect(authHeader()).toEqual({});
  });

  it('localStorage の auth_token から Authorization ヘッダを生成する', () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'jwt-abc');

    expect(authHeader()).toEqual({ Authorization: 'Bearer jwt-abc' });
  });

  it('トークンキーは AuthContext と同じ auth_token である', () => {
    // AuthContext が localStorage 保存に使うキーとずれると認証ヘッダが付かなくなる回帰防止。
    expect(TOKEN_STORAGE_KEY).toBe('auth_token');
  });
});
