import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './authStore';

const sales = { username: 'sales01', roles: ['ROLE_SALES'] as const, token: 't' };

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});

describe('認証ストア', () => {
  it('未ログインではどのロールも持たない', () => {
    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(false);
  });

  it('ログインするとそのロールを持つ', () => {
    useAuthStore.getState().login(sales);

    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(true);
    expect(useAuthStore.getState().hasAnyRole(['ROLE_ACCOUNTANT'])).toBe(false);
  });

  it('いずれかのロールを持てば通す', () => {
    useAuthStore.getState().login(sales);

    expect(useAuthStore.getState().hasAnyRole(['ROLE_ACCOUNTANT', 'ROLE_SALES'])).toBe(true);
  });

  it('ログアウトするとロールを失う', () => {
    useAuthStore.getState().login(sales);
    useAuthStore.getState().logout();

    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().hasAnyRole(['ROLE_SALES'])).toBe(false);
  });

  it('保存されているのが localStorage ではない（共用端末で次の人が入れない）', () => {
    useAuthStore.getState().login(sales);

    expect(localStorage.getItem('cargo-tracker-auth')).toBeNull();
    expect(sessionStorage.getItem('cargo-tracker-auth')).toContain('sales01');
  });
});
