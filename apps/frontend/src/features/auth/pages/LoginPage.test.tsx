import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';
import { AuthContext } from '../contexts/AuthContext';

// AuthContext の型をテスト用に再定義
const mockLogin = vi.fn();
const mockLogout = vi.fn();

function renderLoginPage(loginImpl = mockLogin) {
  const contextValue = {
    token: null,
    role: null,
    username: null,
    isAuthenticated: false,
    login: loginImpl,
    logout: mockLogout,
  };
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={contextValue}>
        <LoginPage />
      </AuthContext.Provider>
    </MemoryRouter>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('ログインフォームが表示される', () => {
    renderLoginPage();
    expect(screen.getByLabelText('ユーザー ID')).toBeInTheDocument();
    expect(screen.getByLabelText('パスワード')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'ログイン' })).toBeInTheDocument();
  });

  it('入力してログインボタンを押すと login が呼ばれる', async () => {
    mockLogin.mockResolvedValue(undefined);
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('ユーザー ID'), 'admin01');
    await user.type(screen.getByLabelText('パスワード'), 'password123');
    await user.click(screen.getByRole('button', { name: 'ログイン' }));

    await waitFor(() =>
      expect(mockLogin).toHaveBeenCalledWith('admin01', 'password123')
    );
  });

  it('login 失敗時にエラーメッセージが表示される', async () => {
    mockLogin.mockRejectedValue(new Error('パスワードが正しくありません'));
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('ユーザー ID'), 'user01');
    await user.type(screen.getByLabelText('パスワード'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'ログイン' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('パスワードが正しくありません')
    );
  });
});
