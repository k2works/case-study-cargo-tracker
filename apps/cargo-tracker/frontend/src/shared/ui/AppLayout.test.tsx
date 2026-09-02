import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppLayout } from './AppLayout';
import { useAuthStore } from '@/shared/auth/authStore';

function renderLayout(initial = '/shippers') {
  return render(
    <MemoryRouter initialEntries={['/', initial]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/shippers" element={<h1>荷主一覧</h1>} />
          <Route path="/" element={<h1>ダッシュボード</h1>} />
        </Route>
        <Route path="/login" element={<h1>ログイン</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});

describe('S03 ログアウト（US27）', () => {
  it('ログアウトすると認証を捨ててログイン画面へ移る', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
    renderLayout();

    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument(),
    );
    expect(useAuthStore.getState().user).toBeNull();
    expect(sessionStorage.getItem('cargo-tracker-auth')).not.toContain('sales01');
  });

  it('ログアウト後にブラウザバックで戻っても保護画面は開かない', async () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
    renderLayout();
    await userEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    await waitFor(() => expect(useAuthStore.getState().user).toBeNull());

    // 戻る操作。replace で遷移しているので履歴には保護画面が残らない想定。
    window.history.back();

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: '荷主一覧' })).not.toBeInTheDocument(),
    );
  });

  it('ヘッダに利用者名とロールが出る', () => {
    useAuthStore.setState({ user: { username: 'sales01', roles: ['ROLE_SALES'], token: 't' } });
    renderLayout();

    expect(screen.getByText(/sales01/)).toBeInTheDocument();
    expect(screen.getByText(/営業担当者/)).toBeInTheDocument();
  });
});
