import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as voyageApi from '../api/voyageApi';
import VoyageFormPage from './VoyageFormPage';

vi.mock('../api/voyageApi');

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/voyages/new']}>
      <Routes>
        <Route path="/voyages/new" element={<VoyageFormPage />} />
        <Route path="/voyages" element={<div>一覧ページ</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function renderEdit(voyageNumber = 'V001') {
  return render(
    <MemoryRouter initialEntries={[`/voyages/${voyageNumber}/edit`]}>
      <Routes>
        <Route path="/voyages/:voyageNumber/edit" element={<VoyageFormPage />} />
        <Route path="/voyages" element={<div>一覧ページ</div>} />
      </Routes>
    </MemoryRouter>
  );
}

const existingVoyage = {
  voyageNumber: 'V001',
  carrierCode: 'CARRIER-A',
  carrierName: '運送会社A',
  shipName: 'SHIPα',
  originUnlocode: 'JPTYO',
  destUnlocode: 'USNYC',
  departureDate: '2026-06-01T10:00:00',
  arrivalDate: '2026-06-10T18:00:00',
  status: 'ACTIVE',
  movements: [],
  acceptedCargoTypes: ['GENERAL'],
};

describe('VoyageFormPage (新規登録)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('新規登録フォームが表示される', () => {
    renderNew();
    expect(screen.getByRole('heading', { name: '航海スケジュール新規登録' })).toBeInTheDocument();
    expect(screen.getByLabelText('航海番号')).toBeInTheDocument();
  });

  it('必須項目が未入力でエラーが表示される', async () => {
    renderNew();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '登録' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toBeInTheDocument()
    );
  });

  it('正常に登録できると一覧ページへ遷移する', async () => {
    vi.mocked(voyageApi.registerVoyage).mockResolvedValue(undefined);
    renderNew();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('航海番号'), 'V001');
    await user.type(screen.getByLabelText('運送会社コード'), 'CARRIER-A');
    await user.type(screen.getByLabelText('運送会社名'), '運送会社A');
    await user.type(screen.getByLabelText('船名'), 'SHIPα');
    await user.type(screen.getByLabelText('出発港 (UN/LOCODE)'), 'JPTYO');
    await user.type(screen.getByLabelText('到着港 (UN/LOCODE)'), 'USNYC');
    await user.type(screen.getByLabelText('出発日'), '2026-06-01T10:00');
    await user.type(screen.getByLabelText('到着日'), '2026-06-10T18:00');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('一覧ページ')).toBeInTheDocument()
    );
  });
});

describe('VoyageFormPage (更新)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('既存データが読み込まれて更新フォームが表示される', async () => {
    vi.mocked(voyageApi.fetchVoyage).mockResolvedValue(existingVoyage);
    renderEdit('V001');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: '航海スケジュール更新' })).toBeInTheDocument()
    );
    expect(screen.getByDisplayValue('2026-06-01T10:00')).toBeInTheDocument();
  });

  it('更新が成功すると一覧ページへ遷移する', async () => {
    vi.mocked(voyageApi.fetchVoyage).mockResolvedValue(existingVoyage);
    vi.mocked(voyageApi.updateVoyage).mockResolvedValue(undefined);
    renderEdit('V001');
    const user = userEvent.setup();

    await waitFor(() => screen.getByRole('heading', { name: '航海スケジュール更新' }));
    await user.click(screen.getByRole('button', { name: '更新' }));

    await waitFor(() =>
      expect(screen.getByText('一覧ページ')).toBeInTheDocument()
    );
  });
});
