import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as trackingApi from '../api/trackingApi';
import ExceptionListPage from './ExceptionListPage';

vi.mock('../api/trackingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/trackingApi')>(
    '../api/trackingApi',
  );
  return {
    ...actual,
    fetchAllExceptions: vi.fn(),
  };
});

const samplePage = {
  items: [
    {
      exceptionId: 'EX-001',
      trackingNumber: 'TRK-AB12CD3456',
      type: 'DELAY' as const,
      occurredAt: '2026-07-25T09:00:00',
      occurredUnlocode: 'SGSIN',
      description: '悪天候',
      responseStatus: 'REPORTED' as const,
      resolution: null,
      resolvedAt: null,
      escalated: false,
      createdAt: null,
      updatedAt: null,
    },
    {
      exceptionId: 'EX-002',
      trackingNumber: 'TRK-XY99ZZ1111',
      type: 'LOSS' as const,
      occurredAt: '2026-07-26T14:00:00',
      occurredUnlocode: 'CNSHA',
      description: '貨物紛失',
      responseStatus: 'REPORTED' as const,
      resolution: null,
      resolvedAt: null,
      escalated: true,
      createdAt: null,
      updatedAt: null,
    },
  ],
  totalCount: 2,
  page: 0,
  size: 20,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/tracking/exceptions']}>
      <Routes>
        <Route path="/tracking/exceptions" element={<ExceptionListPage />} />
        <Route path="/tracking/:trackingNumber/manage" element={<div>追跡詳細</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ExceptionListPage (US19 / US20 例外対応一覧)', () => {
  it('US19/US20: 一覧を表示し、escalated 行に管理職通知済バッジを表示する', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockResolvedValue(samplePage);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });
    expect(screen.getByText('TRK-XY99ZZ1111')).toBeInTheDocument();
    expect(screen.getByText('管理職通知済')).toBeInTheDocument();
    expect(screen.getAllByText('未対応').length).toBeGreaterThanOrEqual(2);
  });

  it('US19: 対応状態フィルタを変更すると fetchAllExceptions が新しい引数で呼ばれる', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockResolvedValue(samplePage);

    renderPage();
    const user = userEvent.setup();

    await waitFor(() => {
      expect(trackingApi.fetchAllExceptions).toHaveBeenCalledWith('ALL', 0, 20);
    });

    await user.selectOptions(screen.getByLabelText('対応状態'), 'REPORTED');

    await waitFor(() => {
      expect(trackingApi.fetchAllExceptions).toHaveBeenCalledWith('REPORTED', 0, 20);
    });
  });

  it('US19: 行クリックで追跡詳細へ遷移する', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockResolvedValue(samplePage);

    renderPage();
    const user = userEvent.setup();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });

    await user.click(screen.getByText('TRK-AB12CD3456'));

    await waitFor(() => {
      expect(screen.getByText('追跡詳細')).toBeInTheDocument();
    });
  });

  it('US19: 例外なしの場合は空状態メッセージを表示する', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockResolvedValue({
      items: [],
      totalCount: 0,
      page: 0,
      size: 20,
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('例外がありません')).toBeInTheDocument();
    });
  });

  it('US19: API エラー時にアラートを表示する', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockRejectedValue(
      new Error('取得失敗'),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('取得失敗');
    });
  });

  it('US20: 件数表示が totalCount を反映する', async () => {
    vi.mocked(trackingApi.fetchAllExceptions).mockResolvedValue(samplePage);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('2 件')).toBeInTheDocument();
    });
  });
});
