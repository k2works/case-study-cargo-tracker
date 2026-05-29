import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as trackingApi from '../api/trackingApi';
import ExceptionRegisterPage from './ExceptionRegisterPage';

vi.mock('../api/trackingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/trackingApi')>(
    '../api/trackingApi',
  );
  return {
    ...actual,
    registerException: vi.fn(),
  };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/tracking/TRK-AB12CD3456/exceptions/new']}>
      <Routes>
        <Route
          path="/tracking/:trackingNumber/exceptions/new"
          element={<ExceptionRegisterPage />}
        />
        <Route path="/tracking/:trackingNumber/manage" element={<div>追跡詳細</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ExceptionRegisterPage (US19 / US20)', () => {
  it('US19: DELAY 例外を登録すると registerException が呼ばれて追跡詳細へ遷移する', async () => {
    vi.mocked(trackingApi.registerException).mockResolvedValue({
      exceptionId: 'EX-001',
      trackingNumber: 'TRK-AB12CD3456',
      type: 'DELAY',
      occurredAt: '2026-07-25T09:00',
      occurredUnlocode: 'SGSIN',
      description: '悪天候',
      responseStatus: 'REPORTED',
      resolution: null,
      resolvedAt: null,
      escalated: false,
      createdAt: null,
      updatedAt: null,
    });

    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('発生場所 (UN/LOCODE)'), 'sgsin');
    await user.type(screen.getByLabelText('発生状況・理由'), '悪天候のため寄港不可');
    await user.click(screen.getByRole('button', { name: '例外を記録' }));

    await waitFor(() => {
      expect(screen.getByText('追跡詳細')).toBeInTheDocument();
    });
    expect(trackingApi.registerException).toHaveBeenCalledWith(
      'TRK-AB12CD3456',
      expect.objectContaining({
        type: 'DELAY',
        occurredUnlocode: 'SGSIN',
        description: '悪天候のため寄港不可',
      }),
    );
  });

  it('US20: LOSS 選択時に escalation 警告が表示される', async () => {
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('radio', { name: '紛失' }));

    expect(screen.getByText('⚠ 紛失を選択しました')).toBeInTheDocument();
    expect(
      screen.getByLabelText(/escalation を承知のうえ登録する/),
    ).toBeInTheDocument();
  });

  it('US20: LOSS で確認チェックなしで送信するとエラーで API は呼ばれない', async () => {
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('radio', { name: '紛失' }));
    await user.type(screen.getByLabelText('発生状況・理由'), '貨物紛失');
    await user.click(screen.getByRole('button', { name: '例外を記録' }));

    await waitFor(() => {
      expect(
        screen.getByText(/escalation 通知の確認が必要/),
      ).toBeInTheDocument();
    });
    expect(trackingApi.registerException).not.toHaveBeenCalled();
  });

  it('US20: LOSS で確認チェックを入れて送信すると registerException が呼ばれる', async () => {
    vi.mocked(trackingApi.registerException).mockResolvedValue({
      exceptionId: 'EX-002',
      trackingNumber: 'TRK-AB12CD3456',
      type: 'LOSS',
      occurredAt: '2026-07-25T09:00',
      occurredUnlocode: null,
      description: '貨物紛失',
      responseStatus: 'REPORTED',
      resolution: null,
      resolvedAt: null,
      escalated: true,
      createdAt: null,
      updatedAt: null,
    });

    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('radio', { name: '紛失' }));
    await user.type(screen.getByLabelText('発生状況・理由'), '貨物紛失');
    await user.click(screen.getByLabelText(/escalation を承知のうえ登録する/));
    await user.click(screen.getByRole('button', { name: '例外を記録' }));

    await waitFor(() => {
      expect(screen.getByText('追跡詳細')).toBeInTheDocument();
    });
    expect(trackingApi.registerException).toHaveBeenCalledWith(
      'TRK-AB12CD3456',
      expect.objectContaining({ type: 'LOSS' }),
    );
  });

  it('US19: description が空ならば送信前に検証エラー（API 未呼出）', async () => {
    renderPage();
    const user = userEvent.setup();

    // description は required 属性で submit がブロックされる前提だが、念のため空のまま送信を試みる
    // HTML5 検証で submit がキャンセルされるため、testid 直接送信ではなく button click で代用
    const descriptionInput = screen.getByLabelText('発生状況・理由');
    expect(descriptionInput).toHaveAttribute('required');

    // API は呼ばれない
    await user.click(screen.getByRole('button', { name: '例外を記録' }));
    expect(trackingApi.registerException).not.toHaveBeenCalled();
  });

  it('US19/US20: 他の種別から LOSS 以外に戻すと escalation 警告は消える', async () => {
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('radio', { name: '紛失' }));
    expect(screen.getByText('⚠ 紛失を選択しました')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: '遅延' }));
    expect(screen.queryByText('⚠ 紛失を選択しました')).not.toBeInTheDocument();
  });
});
