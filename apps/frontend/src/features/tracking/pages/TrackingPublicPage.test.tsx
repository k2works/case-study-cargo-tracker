import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as trackingApi from '../api/trackingApi';
import TrackingPublicPage from './TrackingPublicPage';

vi.mock('../api/trackingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/trackingApi')>(
    '../api/trackingApi',
  );
  return {
    ...actual,
    fetchPublicTracking: vi.fn(),
  };
});

const successPayload = {
  summary: {
    trackingNumber: 'TRK-AB12CD3456',
    bookingId: 'B-001',
    currentStatus: 'IN_TRANSIT' as const,
    currentUnlocode: 'SGSIN',
    currentVoyageNumber: 'V-MAERSK-220',
    estimatedArrival: '2026-08-15T00:00:00',
    misrouted: false,
    lastEventAt: '2026-07-22T14:00:00',
    deliveredAt: null,
  },
  events: [
    {
      eventId: 1,
      occurredAt: '2026-07-20T10:00:00',
      recordedAt: '2026-07-20T10:00:00',
      eventType: 'TRACKING_INITIALIZED',
      transportStatus: 'NOT_RECEIVED' as const,
      unlocode: null,
      voyageNumber: null,
      handlingType: null,
      source: 'SYSTEM' as const,
      description: null,
    },
    {
      eventId: 2,
      occurredAt: '2026-07-22T14:00:00',
      recordedAt: '2026-07-22T14:00:00',
      eventType: 'STATUS_UPDATED',
      transportStatus: 'IN_TRANSIT' as const,
      unlocode: 'SGSIN',
      voyageNumber: 'V-MAERSK-220',
      handlingType: null,
      source: 'MANUAL' as const,
      description: '東京港出港',
    },
  ],
};

function renderPage(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/tracking/:trackingNumber" element={<TrackingPublicPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TrackingPublicPage (US18 公開照会)', () => {
  it('成功時に追跡サマリと履歴を表示する', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockResolvedValue({
      type: 'success',
      payload: successPayload,
    });

    renderPage('/tracking/TRK-AB12CD3456?token=valid-jwt');

    await waitFor(() => {
      expect(screen.getByText('TRK-AB12CD3456')).toBeInTheDocument();
    });
    // 「輸送中」「SGSIN」は summary（dd）と events 表の両方に出現するため複数件
    expect(screen.getAllByText('輸送中').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('SGSIN').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('東京港出港')).toBeInTheDocument();
    expect(trackingApi.fetchPublicTracking).toHaveBeenCalledWith(
      'TRK-AB12CD3456',
      'valid-jwt',
    );
  });

  it('token クエリパラメータがないと API は呼ばれず forbidden 表示になる（早期検出）', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockImplementation(async (_tn, token) => {
      if (!token) {
        return { type: 'forbidden', message: 'トークンが指定されていません' };
      }
      return { type: 'success', payload: successPayload };
    });

    renderPage('/tracking/TRK-AB12CD3456');

    await waitFor(() => {
      expect(screen.getByText('アクセスできません')).toBeInTheDocument();
    });
    expect(screen.getByText('トークンが指定されていません')).toBeInTheDocument();
  });

  it('403 で「リンクの有効期限切れ・担当者再発行」を表示する', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockResolvedValue({
      type: 'forbidden',
      message: 'リンクの有効期限が切れています。担当者に再発行を依頼してください',
    });

    renderPage('/tracking/TRK-AB12CD3456?token=expired-jwt');

    await waitFor(() => {
      expect(
        screen.getByText('リンクの有効期限が切れています。担当者に再発行を依頼してください'),
      ).toBeInTheDocument();
    });
  });

  it('404 で「追跡番号が見つかりません」を表示する', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockResolvedValue({
      type: 'not_found',
    });

    renderPage('/tracking/TRK-NOPE000000?token=valid-jwt');

    await waitFor(() => {
      expect(screen.getByText('追跡番号が見つかりません')).toBeInTheDocument();
    });
  });

  it('配送完了済みの場合は deliveredAt を表示する', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockResolvedValue({
      type: 'success',
      payload: {
        ...successPayload,
        summary: {
          ...successPayload.summary,
          currentStatus: 'DELIVERED',
          deliveredAt: '2026-08-16T14:00:00',
        },
      },
    });

    renderPage('/tracking/TRK-AB12CD3456?token=valid-jwt');

    await waitFor(() => {
      expect(screen.getByText('配送完了日時')).toBeInTheDocument();
    });
    expect(screen.getByText('2026-08-16T14:00:00')).toBeInTheDocument();
  });

  it('時限署名トークンの注意書きを表示する（成功時）', async () => {
    vi.mocked(trackingApi.fetchPublicTracking).mockResolvedValue({
      type: 'success',
      payload: successPayload,
    });

    renderPage('/tracking/TRK-AB12CD3456?token=valid-jwt');

    await waitFor(() => {
      expect(screen.getByText(/時限署名トークン/)).toBeInTheDocument();
    });
  });
});
