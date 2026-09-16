import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from './demoAccounts';

// DEMO_LOGIN はモジュールの読み込み時に決まる。有効な状態の画面を確かめるには
// ここで差し替えるほかない。
vi.mock('./demoLogin', () => ({
  DEMO_LOGIN: {
    enabled: true,
    username: 'sales01',
    password: DEMO_PASSWORD,
    accounts: DEMO_ACCOUNTS,
  },
}));

const { LoginPage } = await import('./LoginPage');

beforeEach(() => {
  sessionStorage.clear();
});

describe('S00 ログイン（開発環境の事前入力）', () => {
  it('開発環境であることを画面に出す', () => {
    // 事前入力を隠すと、本番同様の画面だと思い込まれる。
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    expect(screen.getByText(/開発環境/)).toBeInTheDocument();
  });

  it('既定の利用者と共通パスワードが入力欄に入っている', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    expect(screen.getByLabelText('利用者名')).toHaveValue('sales01');
    expect(screen.getByLabelText('パスワード')).toHaveValue(DEMO_PASSWORD);
  });

  it('一覧の利用者を選ぶと入力欄に反映される', async () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'accountant01' }));

    expect(screen.getByLabelText('利用者名')).toHaveValue('accountant01');
    expect(screen.getByLabelText('パスワード')).toHaveValue(DEMO_PASSWORD);
  });

  it('一覧の利用者すべてを選べる。載せただけで選べないものを作らない', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    for (const account of DEMO_ACCOUNTS) {
      expect(screen.getByRole('button', { name: account.username })).toBeInTheDocument();
      expect(screen.getByText(account.description)).toBeInTheDocument();
    }
  });
});
