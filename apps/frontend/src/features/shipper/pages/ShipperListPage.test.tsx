import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as shipperApi from '../api/shipperApi';
import ShipperListPage from './ShipperListPage';

vi.mock('../api/shipperApi');

const mockShippers = [
  {
    shipperId: 'S-001',
    shipperType: 'INDIVIDUAL',
    name: '山田太郎',
    addressLine1: '東京都千代田区丸の内 1-1',
    addressLine2: null,
    city: '千代田区',
    countryCode: 'JP',
    postalCode: '100-0005',
    email: 'yamada@example.com',
    phone: '03-1234-5678',
    contractNumber: null,
    discountRate: null,
    active: true,
  },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/shippers']}>
      <Routes>
        <Route path="/shippers" element={<ShipperListPage />} />
        <Route path="/shippers/new" element={<div>新規登録フォーム</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ShipperListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('荷主一覧が表示される', async () => {
    vi.mocked(shipperApi.fetchShippers).mockResolvedValue(mockShippers);
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('山田太郎')).toBeInTheDocument()
    );
    expect(screen.getByText('yamada@example.com')).toBeInTheDocument();
  });

  it('新規登録ボタンを押すと登録フォームへ遷移する', async () => {
    vi.mocked(shipperApi.fetchShippers).mockResolvedValue([]);
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '新規登録' }));

    expect(screen.getByText('新規登録フォーム')).toBeInTheDocument();
  });

  it('登録済みの荷主が 0 件の場合にメッセージが表示される', async () => {
    vi.mocked(shipperApi.fetchShippers).mockResolvedValue([]);
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('登録されている荷主はありません')).toBeInTheDocument()
    );
  });

  it('API 取得失敗時にエラーが表示される', async () => {
    vi.mocked(shipperApi.fetchShippers).mockRejectedValue(new Error('API ダウン'));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('API ダウン')
    );
  });
});
