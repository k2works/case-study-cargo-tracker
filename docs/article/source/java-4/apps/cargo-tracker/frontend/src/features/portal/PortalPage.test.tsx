import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { PortalPage } from './PortalPage';

function renderPortal() {
  return render(
    <MemoryRouter initialEntries={['/portal']}>
      <Routes>
        <Route path="/portal" element={<PortalPage />} />
        <Route path="/track/:trackingNumber" element={<h1>荷物の追跡</h1>} />
        <Route path="/login" element={<h1>ログイン</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('S01 ポータル（公開）', () => {
  it('追跡番号を入れると公開追跡へ行ける', async () => {
    // 荷受人はロールを持たない。ロール別の到達性は認証済みの利用者にしか
    // 働かないので、認証の外に入口が要る（ui_design.md）。
    renderPortal();

    await userEvent.type(screen.getByLabelText('追跡番号'), 'ABC12345');
    await userEvent.click(screen.getByRole('button', { name: '照会する' }));

    expect(await screen.findByRole('heading', { name: '荷物の追跡' })).toBeInTheDocument();
  });

  it('空のまま押しても遷移しない', async () => {
    // 番号なしで /track/ へ送ると、追跡番号のない詳細画面に着く。
    renderPortal();

    await userEvent.click(screen.getByRole('button', { name: '照会する' }));

    expect(screen.getByRole('heading', { name: /荷物の追跡照会|ポータル/ })).toBeInTheDocument();
  });

  it('社内の利用者はログインへ行ける', async () => {
    renderPortal();

    await userEvent.click(screen.getByRole('link', { name: /ログイン/ }));

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
  });
});
