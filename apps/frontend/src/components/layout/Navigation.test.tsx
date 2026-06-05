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

  it('IT4: ROLE_ROUTING に経路設計リンクが表示される（H3）', () => {
    renderWithAuth('ROLE_ROUTING');

    expect(screen.getByRole('link', { name: '経路設計' })).toHaveAttribute('href', '/routing/design');
  });

  it('IT4: ROLE_SALES に経路設計リンクは表示されない', () => {
    renderWithAuth('ROLE_SALES');

    expect(screen.queryByRole('link', { name: '経路設計' })).not.toBeInTheDocument();
  });

  it('IT2: ROLE_HANDLER に荷主管理・予約管理リンクは表示されない', () => {
    renderWithAuth('ROLE_HANDLER');

    expect(screen.queryByRole('link', { name: '荷主管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '予約管理' })).not.toBeInTheDocument();
  });

  it('IT5: ROLE_TRACKER に追跡管理リンクが表示される（US17）', () => {
    renderWithAuth('ROLE_TRACKER');

    expect(screen.getByRole('link', { name: '追跡管理' })).toHaveAttribute('href', '/tracking');
  });

  it('IT5: ROLE_SALES に追跡管理リンクは表示されない', () => {
    renderWithAuth('ROLE_SALES');

    expect(screen.queryByRole('link', { name: '追跡管理' })).not.toBeInTheDocument();
  });

  it('IT5: ROLE_HANDLER に荷役作業リンクが表示される（US15）', () => {
    renderWithAuth('ROLE_HANDLER');

    expect(screen.getByRole('link', { name: '荷役作業' })).toHaveAttribute('href', '/handling');
  });

  it('IT5: ROLE_TRACKER に荷役作業リンクは表示されない', () => {
    renderWithAuth('ROLE_TRACKER');

    expect(screen.queryByRole('link', { name: '荷役作業' })).not.toBeInTheDocument();
  });

  it('IT5: ROLE_ADMIN に追跡管理と荷役作業の両方が表示される', () => {
    renderWithAuth('ROLE_ADMIN');

    expect(screen.getByRole('link', { name: '追跡管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '荷役作業' })).toBeInTheDocument();
  });

  it('IT5: ROLE_TRACKER にダッシュボードリンクが表示される', () => {
    renderWithAuth('ROLE_TRACKER');

    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toHaveAttribute('href', '/');
  });

  it('IT7: ROLE_ACCOUNTANT に請求管理・督促一覧リンクが表示される（US23）', () => {
    renderWithAuth('ROLE_ACCOUNTANT');

    expect(screen.getByRole('link', { name: '請求管理' })).toHaveAttribute('href', '/billing');
    expect(screen.getByRole('link', { name: '督促一覧' })).toHaveAttribute('href', '/billing/overdue');
  });

  it('IT7: ROLE_ACCOUNTANT にダッシュボードリンクが表示される（US23）', () => {
    renderWithAuth('ROLE_ACCOUNTANT');

    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toHaveAttribute('href', '/');
  });

  it('IT7: ROLE_SALES に請求管理・督促一覧リンクは表示されない', () => {
    renderWithAuth('ROLE_SALES');

    expect(screen.queryByRole('link', { name: '請求管理' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '督促一覧' })).not.toBeInTheDocument();
  });

  it('IT7: ROLE_ADMIN に請求管理・督促一覧リンクが表示される', () => {
    renderWithAuth('ROLE_ADMIN');

    expect(screen.getByRole('link', { name: '請求管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '督促一覧' })).toBeInTheDocument();
  });
});
