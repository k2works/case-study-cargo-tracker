import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../features/auth/contexts/AuthContext';
import Navigation from './Navigation';

function renderWithAuth(role: string) {
  localStorage.setItem('auth_token', 'dummy-token');
  localStorage.setItem('auth_role', role);
  localStorage.setItem('auth_username', 'tester');
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Navigation />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe('Navigation', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('IT2: ROLE_SALES に荷主管理・予約管理リンクが表示される', () => {
    renderWithAuth('ROLE_SALES');

    expect(screen.getByRole('link', { name: '荷主管理' })).toHaveAttribute('href', '/shippers');
    expect(screen.getByRole('link', { name: '予約管理' })).toHaveAttribute('href', '/bookings');
    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toHaveAttribute('href', '/');
  });

  it('IT2: ROLE_SALES に航海スケジュールリンクは表示されない', () => {
    renderWithAuth('ROLE_SALES');

    expect(screen.queryByRole('link', { name: '航海スケジュール' })).not.toBeInTheDocument();
  });

  it('IT2: ROLE_ADMIN に荷主管理・予約管理・航海スケジュールすべて表示される', () => {
    renderWithAuth('ROLE_ADMIN');

    expect(screen.getByRole('link', { name: '荷主管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '予約管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '航海スケジュール' })).toBeInTheDocument();
  });

  it('IT2: ROLE_ROUTING に荷主管理・予約管理リンクは表示されない', () => {
    renderWithAuth('ROLE_ROUTING');

    expect(screen.queryByRole('link', { name: '荷主管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '予約管理' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '航海スケジュール' })).toBeInTheDocument();
  });

  it('IT2: ROLE_HANDLER に荷主管理・予約管理リンクは表示されない', () => {
    renderWithAuth('ROLE_HANDLER');

    expect(screen.queryByRole('link', { name: '荷主管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '予約管理' })).not.toBeInTheDocument();
  });
});
