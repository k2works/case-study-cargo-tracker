import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as handlingApi from '../api/handlingApi';
import HandlingFormPage from './HandlingFormPage';
import { AuthProvider } from '../../auth/contexts/AuthContext';

vi.mock('../api/handlingApi', async () => {
  const actual = await vi.importActual<typeof import('../api/handlingApi')>(
    '../api/handlingApi',
  );
  return {
    ...actual,
    registerHandling: vi.fn(),
  };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/handling/new']}>
      <AuthProvider>
        <Routes>
          <Route path="/handling/new" element={<HandlingFormPage />} />
          <Route path="/handling" element={<div>履歴ページ</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('HandlingFormPage', () => {
  it('US15: 受領（RECEIVE）登録で API が呼ばれる', async () => {
    vi.mocked(handlingApi.registerHandling).mockResolvedValueOnce({
      activityId: 'A-001',
    });

    renderPage();

    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-AB12CD3456');
    await userEvent.type(screen.getByLabelText('作業場所（UN/LOCODE）'), 'JPTYO');
    await userEvent.type(screen.getByLabelText('作業員 ID'), 'H-001');
    await userEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(handlingApi.registerHandling).toHaveBeenCalledWith(
        expect.objectContaining({
          trackingNumber: 'TRK-AB12CD3456',
          handlingType: 'RECEIVE',
          unlocode: 'JPTYO',
          handlerId: 'H-001',
          claimVerification: null,
        }),
      );
    });
  });

  it('US15: LOAD では航海番号必須のヒントが出る', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/作業種別/), 'LOAD');
    expect(screen.getByText('LOAD / UNLOAD では必須')).toBeInTheDocument();
  });

  it('US16: CLAIM 選択で荷受人確認フィールドが表示される', async () => {
    renderPage();
    await userEvent.selectOptions(screen.getByLabelText(/作業種別/), 'CLAIM');
    expect(screen.getByText('荷受人確認（引取必須）')).toBeInTheDocument();
    expect(screen.getByLabelText(/荷受人氏名/)).toBeInTheDocument();
    expect(screen.getByLabelText('署名参照（URL / ID）')).toBeInTheDocument();
    expect(screen.getByLabelText('確認コード')).toBeInTheDocument();
  });

  it('US16: CLAIM で荷受人氏名のみ入力（署名も確認コードも空）はクライアントバリデーションで拒否', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-AB12CD3456');
    await userEvent.type(screen.getByLabelText('作業場所（UN/LOCODE）'), 'USNYC');
    await userEvent.type(screen.getByLabelText('作業員 ID'), 'H-001');
    await userEvent.selectOptions(screen.getByLabelText(/作業種別/), 'CLAIM');
    await userEvent.type(screen.getByLabelText(/荷受人氏名/), '山田太郎');
    await userEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      // role="alert" のエラーメッセージで判定（form 内の helper text と区別）
      expect(screen.getByRole('alert')).toHaveTextContent(
        /引取作業では署名参照または確認コードのいずれかが必須です/,
      );
    });
    expect(handlingApi.registerHandling).not.toHaveBeenCalled();
  });

  it('H4: ログイン中ユーザー名で作業員 ID が初期化される', () => {
    localStorage.setItem('auth_token', 'tok');
    localStorage.setItem('auth_role', 'ROLE_HANDLER');
    localStorage.setItem('auth_username', 'handler01');

    renderPage();

    const handlerIdInput = screen.getByLabelText('作業員 ID') as HTMLInputElement;
    expect(handlerIdInput.value).toBe('handler01');
  });

  it('H4: 直前の場所 / 航海 / 作業員 ID が localStorage から復元される', () => {
    localStorage.setItem('handling_last_unlocode', 'USNYC');
    localStorage.setItem('handling_last_voyage', 'V-MAERSK-220');
    localStorage.setItem('handling_last_handler_id', 'H-PREV');

    renderPage();

    expect((screen.getByLabelText('作業場所（UN/LOCODE）') as HTMLInputElement).value).toBe('USNYC');
    expect((screen.getByLabelText(/航海番号/) as HTMLInputElement).value).toBe('V-MAERSK-220');
    expect((screen.getByLabelText('作業員 ID') as HTMLInputElement).value).toBe('H-PREV');
  });

  it('H4: 登録成功時に直前値が localStorage に保存される', async () => {
    vi.mocked(handlingApi.registerHandling).mockResolvedValueOnce({
      activityId: 'A-LAST',
    });

    renderPage();

    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-AB12CD3456');
    await userEvent.type(screen.getByLabelText('作業場所（UN/LOCODE）'), 'JPTYO');
    await userEvent.selectOptions(screen.getByLabelText(/作業種別/), 'LOAD');
    await userEvent.type(screen.getByLabelText(/航海番号/), 'V-MAERSK-220');
    await userEvent.type(screen.getByLabelText('作業員 ID'), 'H-NEXT');
    await userEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(handlingApi.registerHandling).toHaveBeenCalled();
    });
    expect(localStorage.getItem('handling_last_unlocode')).toBe('JPTYO');
    expect(localStorage.getItem('handling_last_voyage')).toBe('V-MAERSK-220');
    expect(localStorage.getItem('handling_last_handler_id')).toBe('H-NEXT');
  });

  it('US16: CLAIM で荷受人氏名 + 確認コードを入力すれば登録できる', async () => {
    vi.mocked(handlingApi.registerHandling).mockResolvedValueOnce({
      activityId: 'A-002',
    });

    renderPage();
    await userEvent.type(screen.getByLabelText('追跡番号'), 'TRK-AB12CD3456');
    await userEvent.type(screen.getByLabelText('作業場所（UN/LOCODE）'), 'USNYC');
    await userEvent.type(screen.getByLabelText('作業員 ID'), 'H-001');
    await userEvent.selectOptions(screen.getByLabelText(/作業種別/), 'CLAIM');
    await userEvent.type(screen.getByLabelText(/荷受人氏名/), '山田太郎');
    await userEvent.type(screen.getByLabelText('確認コード'), 'A1B2C3');
    await userEvent.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(handlingApi.registerHandling).toHaveBeenCalledWith(
        expect.objectContaining({
          handlingType: 'CLAIM',
          claimVerification: expect.objectContaining({
            consigneeName: '山田太郎',
            confirmationCode: 'A1B2C3',
          }),
        }),
      );
    });
  });
});
