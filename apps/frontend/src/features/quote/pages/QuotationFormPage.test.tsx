import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as voyageApi from '../../voyage/api/voyageApi';
import * as quoteApi from '../api/quoteApi';
import QuotationFormPage from './QuotationFormPage';

vi.mock('../../voyage/api/voyageApi');
vi.mock('../api/quoteApi');

const mockVoyage = {
  voyageNumber: 'V001',
  carrierCode: 'MOL',
  carrierName: 'MOL Line',
  shipName: 'Ship1',
  originUnlocode: 'JPTYO',
  destUnlocode: 'USNYC',
  departureDate: '2026-06-05T10:00:00',
  arrivalDate: '2026-06-20T18:00:00',
  status: 'SCHEDULED',
  movements: [],
  acceptedCargoTypes: ['GENERAL'],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/quotes/new']}>
      <Routes>
        <Route path="/quotes/new" element={<QuotationFormPage />} />
        <Route path="/quotes" element={<div>見積一覧</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('QuotationFormPage (US01 / US07 連携)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('US07: 航海を検索すると候補が表示される', async () => {
    vi.mocked(voyageApi.searchVoyages).mockResolvedValue([mockVoyage]);
    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('出発地'), 'JPTYO');
    await user.type(screen.getByLabelText('目的地'), 'USNYC');
    await user.click(screen.getByRole('button', { name: '航海を検索' }));

    await waitFor(() => expect(screen.getByText('V001')).toBeInTheDocument());
    expect(screen.getByText('MOL Line')).toBeInTheDocument();
  });

  it('US07: 該当航海がない場合は再検索を促すメッセージが表示される', async () => {
    vi.mocked(voyageApi.searchVoyages).mockResolvedValue([]);
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '航海を検索' }));

    await waitFor(() =>
      expect(screen.getByText(/条件に合致する航海がありません/)).toBeInTheDocument()
    );
  });

  it('US01: 候補を選択して見積を作成すると一覧へ遷移する', async () => {
    vi.mocked(voyageApi.searchVoyages).mockResolvedValue([mockVoyage]);
    vi.mocked(quoteApi.createQuotation).mockResolvedValue({ quotationId: 'Q-001' });
    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('荷主 ID'), 'S-001');
    await user.type(screen.getByLabelText('出発地'), 'JPTYO');
    await user.type(screen.getByLabelText('目的地'), 'USNYC');
    await user.type(screen.getByLabelText('重量(kg)'), '1500');
    await user.click(screen.getByRole('button', { name: '航海を検索' }));
    await waitFor(() => screen.getByText('V001'));
    await user.click(screen.getByLabelText('候補 V001'));
    await user.click(screen.getByRole('button', { name: '見積を作成' }));

    await waitFor(() => expect(screen.getByText('見積一覧')).toBeInTheDocument());
    expect(quoteApi.createQuotation).toHaveBeenCalledTimes(1);
    const arg = vi.mocked(quoteApi.createQuotation).mock.calls[0][0];
    expect(arg.candidates).toHaveLength(1);
    expect(arg.candidates[0].estimatedCost).toBe(750000);
  });

  it('US07: 検索失敗時にエラーが表示される', async () => {
    vi.mocked(voyageApi.searchVoyages).mockRejectedValue(new Error('検索ダウン'));
    renderPage();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '航海を検索' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('検索ダウン'));
  });
});
