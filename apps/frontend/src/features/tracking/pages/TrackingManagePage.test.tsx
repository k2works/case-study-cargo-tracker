import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as trackingApi from '../api/trackingApi';
import TrackingManagePage from './TrackingManagePage';

vi.mock('../api/trackingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/trackingApi')>(
    '../api/trackingApi',
  );
  return {
    ...actual,
    fetchTracking: vi.fn(),
    fetchTrackingEvents: vi.fn(),
    updateTrackingStatus: vi.fn(),
  };
});

const summaryNotReceived = {
  trackingNumber: 'TRK-AB12CD3456',
  bookingId: 'B-001',
  currentStatus: 'NOT_RECEIVED' as const,
  currentUnlocode: null,
  currentVoyageNumber: null,
  estimatedArrival: null,
  misrouted: false,
  lastEventAt: null,
  deliveredAt: null,
};

const eventInit = {
  eventId: 1,
  occurredAt: '2026-07-19T10:00:00',
  recordedAt: '2026-07-19T10:00:00',
  eventType: 'TRACKING_INITIALIZED',
  transportStatus: 'NOT_RECEIVED' as const,
  unlocode: null,
  voyageNumber: null,
  handlingType: null,
  source: 'SYSTEM' as const,
  description: null,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/tracking/TRK-AB12CD3456/manage']}>
      <Routes>
        <Route path="/tracking/:trackingNumber/manage" element={<TrackingManagePage />} />
        <Route path="/tracking" element={<div>一覧画面</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TrackingManagePage', () => {
  it('US17: 現在状態と履歴を表示する', async () => {
    vi.mocked(trackingApi.fetchTracking).mockResolvedValueOnce(summaryNotReceived);
    vi.mocked(trackingApi.fetchTrackingEvents).mockResolvedValueOnce([eventInit]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });
    // 「未受領」は現在状態セクションと履歴の transportStatus 列の両方に出るため getAllByText
    expect(screen.getAllByText('未受領').length).toBeGreaterThan(0);
    expect(screen.getByText('TRACKING_INITIALIZED')).toBeInTheDocument();
  });

  it('US17: 許可遷移のみ選択肢に出る（NOT_RECEIVED から RECEIVED/MISROUTED/EXCEPTION のみ）', async () => {
    vi.mocked(trackingApi.fetchTracking).mockResolvedValueOnce(summaryNotReceived);
    vi.mocked(trackingApi.fetchTrackingEvents).mockResolvedValueOnce([eventInit]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });

    const select = screen.getByLabelText('遷移先状態') as HTMLSelectElement;
    const optionTexts = Array.from(select.options).map((o) => o.text);
    expect(optionTexts).toContain('選択してください');
    expect(optionTexts).toContain('受領済');
    expect(optionTexts).toContain('誤配送');
    expect(optionTexts).toContain('例外発生');
    expect(optionTexts).not.toContain('配送完了');
    expect(optionTexts).not.toContain('積込済');
  });

  it('US17: 状態更新コマンドを送信し成功メッセージを表示する', async () => {
    vi.mocked(trackingApi.fetchTracking).mockResolvedValueOnce(summaryNotReceived);
    vi.mocked(trackingApi.fetchTrackingEvents).mockResolvedValueOnce([eventInit]);
    vi.mocked(trackingApi.updateTrackingStatus).mockResolvedValueOnce(undefined);
    // 再読込時のモック
    vi.mocked(trackingApi.fetchTracking).mockResolvedValueOnce({
      ...summaryNotReceived,
      currentStatus: 'RECEIVED',
      currentUnlocode: 'JPTYO',
    });
    vi.mocked(trackingApi.fetchTrackingEvents).mockResolvedValueOnce([eventInit]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });

    await userEvent.selectOptions(screen.getByLabelText('遷移先状態'), 'RECEIVED');
    await userEvent.type(screen.getByLabelText('現在地（UN/LOCODE）'), 'JPTYO');
    await userEvent.click(screen.getByRole('button', { name: '状態を更新' }));

    await waitFor(() => {
      expect(screen.getByText(/状態を 受領済 に更新しました/)).toBeInTheDocument();
    });
    expect(trackingApi.updateTrackingStatus).toHaveBeenCalledWith(
      'TRK-AB12CD3456',
      expect.objectContaining({ toStatus: 'RECEIVED', unlocode: 'JPTYO' }),
    );
  });

  it('US17: 終端状態（DELIVERED）では遷移フォームを表示せずガイダンスのみ', async () => {
    vi.mocked(trackingApi.fetchTracking).mockResolvedValueOnce({
      ...summaryNotReceived,
      currentStatus: 'DELIVERED',
    });
    vi.mocked(trackingApi.fetchTrackingEvents).mockResolvedValueOnce([eventInit]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/配送完了.*から更新可能な遷移はありません/)).toBeInTheDocument();
    });
    expect(screen.queryByLabelText('遷移先状態')).not.toBeInTheDocument();
  });
});
