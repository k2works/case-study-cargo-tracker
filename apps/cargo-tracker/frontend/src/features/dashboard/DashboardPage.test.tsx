import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from '@/shared/auth/authStore';
import type { Role } from '@/shared/auth/roles';
import { DashboardPage } from './DashboardPage';

function renderAs(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'u', roles: [...roles], token: 't' } });
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});

describe('S02 ダッシュボード', () => {
  it('そのロールで開ける画面を「今日の作業」に並べる', () => {
    renderAs(['ROLE_SALES']);

    expect(screen.getByRole('link', { name: '荷主一覧' })).toHaveAttribute('href', '/shippers');
    expect(screen.getByRole('link', { name: '荷主登録' })).toBeInTheDocument();
    // ダッシュボード自身は入口に並べない。今いる画面へのリンクは仕事を進めない。
    expect(screen.queryByRole('link', { name: 'ダッシュボード' })).not.toBeInTheDocument();
  });

  it('入口が無いロールには理由を出す', () => {
    // 空の一覧を黙って出すと「読み込みに失敗した」と受け取られる。
    renderAs(['ROLE_HANDLER']);

    expect(screen.getByText(/次のイテレーションから増えていきます/)).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
