import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { PublicTrackingPage } from './PublicTrackingPage';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/track" element={<PublicTrackingPage />} />
        <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('S44 公開追跡照会', () => {
  it('追跡番号つきで開くと番号を出す', () => {
    renderAt('/track/ABC12345');

    expect(screen.getByRole('heading', { name: '荷物の追跡' })).toBeInTheDocument();
    expect(screen.getByText('ABC12345')).toBeInTheDocument();
  });

  it('番号なしでも開ける（認証の外の入口）', () => {
    // 荷受人はロールを持たない。ここが 404 になると社外からは入れない。
    renderAt('/track');

    expect(screen.getByRole('heading', { name: '荷物の追跡' })).toBeInTheDocument();
    expect(screen.queryByText(/追跡番号:/)).not.toBeInTheDocument();
  });

  it('まだ使えないことを目立たせ、次の行動を書く', () => {
    // 本文に紛れさせると、番号を打ち込んで反応が無いのを不具合と受け取られる。
    renderAt('/track/ABC12345');

    expect(screen.getByText(/次のイテレーションで使えるようになります/)).toBeInTheDocument();
    expect(screen.getByText(/担当の営業までお問い合わせください/)).toBeInTheDocument();
  });

  it('別の番号で照会し直せる', () => {
    renderAt('/track/ABC12345');

    expect(screen.getByRole('link', { name: '別の追跡番号で照会する' }))
      .toHaveAttribute('href', '/portal');
  });
});
