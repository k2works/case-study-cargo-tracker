import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IdleTimeout } from './IdleTimeout';
import { useAuthStore } from './authStore';
import type { Role } from './roles';

function renderWith(roles: readonly Role[]) {
  useAuthStore.setState({ user: { username: 'u', roles, token: 't' } });
  return render(
    <MemoryRouter>
      <IdleTimeout />
    </MemoryRouter>,
  );
}

function advanceMinutes(minutes: number) {
  act(() => {
    vi.advanceTimersByTime(minutes * 60_000);
  });
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  sessionStorage.clear();
});
afterEach(() => {
  vi.useRealTimers();
  useAuthStore.setState({ user: null });
});

describe('無操作タイムアウト', () => {
  it('15 分で警告し、入力中の内容が保存されないことを告げる', () => {
    renderWith(['ROLE_SALES']);

    advanceMinutes(14);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    advanceMinutes(2);
    // 何が起きるかだけでなく、何を失うかを言う。
    expect(screen.getByRole('alert')).toHaveTextContent(/入力中の内容は保存されません/);
  });

  it('20 分で認証を捨てる', () => {
    renderWith(['ROLE_SALES']);

    advanceMinutes(21);

    // 共用端末に開きっぱなしの画面を残さない。
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('操作すると数え直す', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderWith(['ROLE_SALES']);

    advanceMinutes(16);
    expect(screen.getByRole('alert')).toBeInTheDocument();

    await user.click(document.body);

    // 警告が消え、そこからまた 15 分あることを確かめる。消えるだけを見ると、
    // 数え直していない実装でも緑になる。
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    advanceMinutes(14);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    advanceMinutes(2);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('荷役ロールは 20 分では切れない', () => {
    renderWith(['ROLE_HANDLER']);

    advanceMinutes(21);

    expect(useAuthStore.getState().user).not.toBeNull();

    advanceMinutes(40);
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('未ログインでは何もしない', () => {
    useAuthStore.setState({ user: null });
    render(
      <MemoryRouter>
        <IdleTimeout />
      </MemoryRouter>,
    );

    advanceMinutes(30);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
