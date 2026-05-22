import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as voyageApi from '../api/voyageApi';
import VoyageListPage from './VoyageListPage';

vi.mock('../api/voyageApi');

const mockVoyages = [
  {
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
  },
];

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/voyages']}>
      <Routes>
        <Route path="/voyages" element={<VoyageListPage />} />
        <Route path="/voyages/new" element={<div>新規登録フォーム</div>} />
        <Route path="/voyages/:voyageNumber/edit" element={<div>更新フォーム</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('VoyageListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('航海スケジュール一覧が表示される', async () => {
    vi.mocked(voyageApi.fetchVoyages).mockResolvedValue(mockVoyages);
    renderPage();

    await waitFor(() =>
      expect(screen.getByText('V001')).toBeInTheDocument()
    );
    expect(screen.getByText('運送会社A')).toBeInTheDocument();
    expect(screen.getByText('SHIPα')).toBeInTheDocument();
  });

  it('新規登録ボタンを押すと登録フォームへ遷移する', async () => {
    vi.mocked(voyageApi.fetchVoyages).mockResolvedValue([]);
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '新規登録' }));

    expect(screen.getByText('新規登録フォーム')).toBeInTheDocument();
  });

  it('編集ボタンを押すと更新フォームへ遷移する', async () => {
    vi.mocked(voyageApi.fetchVoyages).mockResolvedValue(mockVoyages);
    renderPage();
    const user = userEvent.setup();

    await waitFor(() => screen.getByText('V001'));
    await user.click(screen.getByRole('button', { name: '編集' }));

    expect(screen.getByText('更新フォーム')).toBeInTheDocument();
  });
});
