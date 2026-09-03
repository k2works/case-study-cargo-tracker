import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppRoutes } from './routes';
import { useAuthStore } from '@/shared/auth/authStore';
import { ROLES, type Role } from '@/shared/auth/roles';
import { NAVIGATION, navigationFor } from '@/shared/ui/navigation';

function loginAs(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'tester', roles, token: 't' } });
}

function renderAt(path: string) {
  // 画面が問い合わせを始めたので、本番と同じ器で描く。
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  useAuthStore.setState({ user: null });
});

describe('ロール別の到達性', () => {
  // ナビに出る画面は必ず開ける。出さない画面は 403 にする。
  // 片方だけ確かめると「ナビには出るのに開くと 403」に気づけない。
  it.each(ROLES)('%s: ナビに出る画面はすべて開ける', (role) => {
    for (const item of navigationFor([role])) {
      loginAs([role]);
      const { unmount } = renderAt(item.path);
      expect(
        screen.queryByText('この画面を開く権限がありません'),
        `${role} は ${item.path}（${item.label}）がナビに出るのに開けない`,
      ).not.toBeInTheDocument();
      unmount();
    }
  });

  it.each(ROLES)('%s: ナビに出ない画面は 403 になる', (role) => {
    const allowedPaths = navigationFor([role]).map((i) => i.path);
    for (const item of NAVIGATION.filter((i) => !allowedPaths.includes(i.path))) {
      loginAs([role]);
      const { unmount } = renderAt(item.path);
      expect(
        screen.getByText('この画面を開く権限がありません'),
        `${role} は ${item.path} がナビに出ないのに開けてしまう`,
      ).toBeInTheDocument();
      unmount();
    }
  });

  it('未認証はログイン画面へ送られる（403 ではない）', () => {
    renderAt('/shippers');

    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
  });

  it('ナビの全項目にルートが対応している', () => {
    loginAs(['ROLE_ADMIN', 'ROLE_SALES', 'ROLE_ACCOUNTANT', 'ROLE_TRACKER']);

    for (const item of NAVIGATION) {
      const { unmount } = renderAt(item.path);
      // ルートが無ければ "*" が拾ってダッシュボードに飛ぶ。飛んだら対応漏れ。
      expect(document.body.textContent, `${item.path} にルートが無い`).not.toBe('');
      unmount();
    }
  });
});

describe('ダッシュボードの「今日の作業」', () => {
  it('自分のロールで開ける画面への導線が出る', () => {
    loginAs(['ROLE_SALES']);
    renderAt('/');

    // サイドナビにも同じリンクがあるので、本文（main）に絞って確かめる。
    // ダッシュボードの「今日の作業」から行けることが見たいこと。
    const main = screen.getByRole('main');
    expect(within(main).getByRole('link', { name: '荷主一覧' })).toBeInTheDocument();
    expect(within(main).getByRole('link', { name: '要確認一覧' })).toBeInTheDocument();
  });

  it('開けない画面への導線は出さない', () => {
    loginAs(['ROLE_HANDLER']);
    renderAt('/');

    const main = screen.getByRole('main');
    expect(within(main).queryByRole('link', { name: '荷主一覧' })).not.toBeInTheDocument();
  });
});

describe('403 の見え方', () => {
  it('認証済みの利用者はサイドナビを失わない', () => {
    // 権限の無い画面を開いただけで、その利用者が本来行ける画面への導線まで
    // 消えると、戻る手段が本文のリンク 1 本になる（IT1 レビュー M2）。
    loginAs(['ROLE_SALES']);
    renderAt('/admin/users');

    expect(screen.getByText('この画面を開く権限がありません')).toBeInTheDocument();
    expect(
      screen.getByRole('navigation'),
      '403 でもサイドナビは残る',
    ).toBeInTheDocument();
    expect(within(screen.getByRole('navigation')).getByText('荷主一覧')).toBeInTheDocument();
  });
});
