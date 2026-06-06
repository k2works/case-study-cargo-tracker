import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../../auth/contexts/AuthContext';
import DashboardPage from './DashboardPage';

function renderWithAuth(role: string, username = 'tester') {
  localStorage.setItem('auth_token', 'dummy-token');
  localStorage.setItem('auth_role', role);
  localStorage.setItem('auth_username', username);
  return render(
    <MemoryRouter>
      <AuthProvider>
        <DashboardPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

function getCardLink(title: string): HTMLAnchorElement | null {
  const heading = screen.queryByRole('heading', { name: title, level: 2 });
  return heading ? heading.closest('a') : null;
}

function expectCard(title: string, href: string) {
  const link = getCardLink(title);
  expect(link).not.toBeNull();
  expect(link).toHaveAttribute('href', href);
}

function expectNoCard(title: string) {
  expect(screen.queryByRole('heading', { name: title, level: 2 })).not.toBeInTheDocument();
}

describe('DashboardPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('見出し「ダッシュボード」とユーザー名のウェルカム文を表示する', () => {
    renderWithAuth('ROLE_SALES', 'sales1');

    expect(screen.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeInTheDocument();
    expect(screen.getByText(/sales1/)).toBeInTheDocument();
  });

  it('ROLE_SALES に営業・予約セクションのカードが表示される', () => {
    renderWithAuth('ROLE_SALES');

    expectCard('予約管理', '/bookings');
    expectCard('荷主管理', '/shippers');
    expectCard('見積管理', '/quotes');
  });

  it('ROLE_SALES に経路・航海セクションのカードは表示されない', () => {
    renderWithAuth('ROLE_SALES');

    expectNoCard('航海スケジュール');
    expectNoCard('経路設計');
  });

  it('ROLE_ROUTING に航海スケジュール・経路設計カードが表示される', () => {
    renderWithAuth('ROLE_ROUTING');

    expectCard('航海スケジュール', '/voyages');
    expectCard('経路設計', '/routing/design');
  });

  it('ROLE_TRACKER に追跡管理・例外対応カードが表示される', () => {
    renderWithAuth('ROLE_TRACKER');

    expectCard('追跡管理', '/tracking');
    expectCard('例外対応', '/tracking/exceptions');
  });

  it('ROLE_HANDLER に荷役作業カードが表示される', () => {
    renderWithAuth('ROLE_HANDLER');

    expectCard('荷役作業', '/handling');
  });

  it('ROLE_ACCOUNTANT に請求一覧・督促一覧カードが表示される', () => {
    renderWithAuth('ROLE_ACCOUNTANT');

    expectCard('請求一覧', '/billing');
    expectCard('督促一覧', '/billing/overdue');
  });

  it('ROLE_ADMIN にはすべてのセクションのカードが表示される', () => {
    renderWithAuth('ROLE_ADMIN');

    expectCard('予約管理', '/bookings');
    expectCard('荷主管理', '/shippers');
    expectCard('見積管理', '/quotes');
    expectCard('航海スケジュール', '/voyages');
    expectCard('経路設計', '/routing/design');
    expectCard('追跡管理', '/tracking');
    expectCard('例外対応', '/tracking/exceptions');
    expectCard('荷役作業', '/handling');
    expectCard('請求一覧', '/billing');
    expectCard('督促一覧', '/billing/overdue');
  });

  it('ROLE_ACCOUNTANT に荷役作業カードは表示されない', () => {
    renderWithAuth('ROLE_ACCOUNTANT');

    expectNoCard('荷役作業');
    expectNoCard('航海スケジュール');
  });
});
