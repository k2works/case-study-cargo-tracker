import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as bookingApi from '../api/bookingApi';
import * as shipperApi from '../../shipper/api/shipperApi';
import BookingFormPage from './BookingFormPage';

vi.mock('../api/bookingApi');
vi.mock('../../shipper/api/shipperApi');

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/bookings/new']}>
      <Routes>
        <Route path="/bookings/new" element={<BookingFormPage />} />
        <Route path="/bookings" element={<div>予約一覧</div>} />
      </Routes>
    </MemoryRouter>
  );
}

/** 見積からの予約化（H4）で渡される navigation state を付けてレンダリングする。 */
function renderWithPreset(preset: Record<string, unknown>) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/bookings/new', state: { fromQuotation: preset } }]}>
      <Routes>
        <Route path="/bookings/new" element={<BookingFormPage />} />
        <Route path="/bookings" element={<div>予約一覧</div>} />
      </Routes>
    </MemoryRouter>
  );
}

const mockShippers = [
  {
    shipperId: 'S-001',
    shipperType: 'INDIVIDUAL',
    name: '山田太郎',
    addressLine1: '東京都千代田区',
    addressLine2: null,
    city: '千代田区',
    countryCode: 'JP',
    postalCode: null,
    email: 'yamada@example.com',
    phone: '03-1234-5678',
    contractNumber: null,
    discountRate: null,
    active: true,
  },
];

async function fillRequired(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText('荷主'), 'S-001');
  await user.type(screen.getByLabelText('出発地 (UN/LOCODE)'), 'JPTYO');
  await user.type(screen.getByLabelText('目的地 (UN/LOCODE)'), 'USNYC');
  await user.type(screen.getByLabelText('到着期限'), '2026-09-30');
  await user.type(screen.getByLabelText('重量 (kg)'), '1500');
  await user.type(screen.getByLabelText('数量'), '10');
  await user.type(screen.getByLabelText('品名'), '電子部品');
}

describe('BookingFormPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(shipperApi.fetchShippersPage).mockResolvedValue({
      items: mockShippers,
      totalCount: mockShippers.length,
      page: 0,
      size: 200,
    });
  });

  it('US04: 新規予約フォームが表示される', async () => {
    renderNew();
    expect(screen.getByRole('heading', { name: '貨物予約新規登録' })).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByLabelText('荷主')).toBeInTheDocument()
    );
    expect(screen.getByLabelText('品名')).toBeInTheDocument();
  });

  it('H4: 見積からの予約化で見積情報がフォームにプリセットされる', async () => {
    renderWithPreset({
      shipperId: 'S-001',
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'USNYC',
      arrivalDeadline: '2026-09-30',
      cargoType: 'GENERAL',
      weightKg: 1500,
    });

    await waitFor(() => screen.getByLabelText('荷主'));
    expect(screen.getByLabelText('出発地 (UN/LOCODE)')).toHaveValue('JPTYO');
    expect(screen.getByLabelText('目的地 (UN/LOCODE)')).toHaveValue('USNYC');
    expect(screen.getByLabelText('到着期限')).toHaveValue('2026-09-30');
    expect(screen.getByLabelText('重量 (kg)')).toHaveValue(1500);
    await waitFor(() => expect(screen.getByLabelText('荷主')).toHaveValue('S-001'));
  });

  it('H4: weightKg を持たない見積プリセットでも重量欄が空のまま壊れない（他項目は反映）', async () => {
    renderWithPreset({
      shipperId: 'S-001',
      originUnlocode: 'JPTYO',
      destinationUnlocode: 'USNYC',
      arrivalDeadline: '2026-09-30',
      cargoType: 'GENERAL',
      weightKg: null,
    });

    await waitFor(() => screen.getByLabelText('荷主'));
    // weightKg=null の無効同値：重量欄は空（number input の空は value=null）
    expect(screen.getByLabelText('重量 (kg)')).toHaveValue(null);
    // 他項目は通常どおりプリセットされる
    expect(screen.getByLabelText('出発地 (UN/LOCODE)')).toHaveValue('JPTYO');
    expect(screen.getByLabelText('目的地 (UN/LOCODE)')).toHaveValue('USNYC');
  });

  it('US04: 荷主選択は既存荷主の一覧から行える', async () => {
    renderNew();

    await waitFor(() =>
      expect(screen.getByRole('option', { name: /山田太郎/ })).toBeInTheDocument()
    );
  });

  it('US04: 必須項目が未入力でエラーが表示される', async () => {
    renderNew();
    const user = userEvent.setup();
    await waitFor(() => screen.getByLabelText('荷主'));
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toBeInTheDocument()
    );
  });

  it('US04: 正常に予約できると一覧ページへ遷移する', async () => {
    vi.mocked(bookingApi.bookCargo).mockResolvedValue({ bookingId: 'B-NEW' });
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('予約一覧')).toBeInTheDocument()
    );

    expect(bookingApi.bookCargo).toHaveBeenCalledWith(
      expect.objectContaining({
        shipperId: 'S-001',
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'USNYC',
        arrivalDeadline: '2026-09-30',
        cargoType: 'GENERAL',
        weightKg: 1500,
        quantity: 10,
        productName: '電子部品',
      })
    );
  });

  it('US04: 寸法は任意項目として送信できる', async () => {
    vi.mocked(bookingApi.bookCargo).mockResolvedValue({ bookingId: 'B-NEW' });
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.type(screen.getByLabelText('長さ (cm)'), '120');
    await user.type(screen.getByLabelText('幅 (cm)'), '80');
    await user.type(screen.getByLabelText('高さ (cm)'), '60');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('予約一覧')).toBeInTheDocument()
    );

    expect(bookingApi.bookCargo).toHaveBeenCalledWith(
      expect.objectContaining({
        lengthCm: 120,
        widthCm: 80,
        heightCm: 60,
      })
    );
  });

  it('US05: 危険物選択で hazard 情報フィールドが表示される', async () => {
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    expect(screen.queryByLabelText('IMO 分類クラス')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS');

    expect(screen.getByLabelText('IMO 分類クラス')).toBeInTheDocument();
    expect(screen.getByLabelText('国連番号')).toBeInTheDocument();
    expect(screen.getByLabelText('申告文')).toBeInTheDocument();
  });

  it('US05: 冷凍選択で温度フィールドが表示される', async () => {
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    expect(screen.queryByLabelText('最低温度 (℃)')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('貨物種別'), 'REFRIGERATED');

    expect(screen.getByLabelText('最低温度 (℃)')).toBeInTheDocument();
    expect(screen.getByLabelText('最高温度 (℃)')).toBeInTheDocument();
  });

  it('US05: 危険物選択で hazard フィールド未入力時にエラー', async () => {
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('危険物')
    );
    expect(bookingApi.bookCargo).not.toHaveBeenCalled();
  });

  it('US05: 冷凍選択で min > max ならエラー', async () => {
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'REFRIGERATED');
    await user.type(screen.getByLabelText('最低温度 (℃)'), '-10');
    await user.type(screen.getByLabelText('最高温度 (℃)'), '-25');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('最低温度は最高温度以下')
    );
    expect(bookingApi.bookCargo).not.toHaveBeenCalled();
  });

  it('US05: 危険物予約が正しく送信される', async () => {
    vi.mocked(bookingApi.bookCargo).mockResolvedValue({ bookingId: 'B-NEW' });
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS');
    await user.type(screen.getByLabelText('IMO 分類クラス'), '3');
    await user.type(screen.getByLabelText('国連番号'), 'UN1090');
    await user.type(screen.getByLabelText('申告文'), '引火性液体');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('予約一覧')).toBeInTheDocument()
    );

    expect(bookingApi.bookCargo).toHaveBeenCalledWith(
      expect.objectContaining({
        cargoType: 'HAZARDOUS',
        hazardImoClass: '3',
        hazardUnNumber: 'UN1090',
        hazardDeclaration: '引火性液体',
        temperatureMinC: null,
        temperatureMaxC: null,
      })
    );
  });

  it('US05: 冷凍予約が正しく送信される', async () => {
    vi.mocked(bookingApi.bookCargo).mockResolvedValue({ bookingId: 'B-NEW' });
    renderNew();
    const user = userEvent.setup();

    await waitFor(() => screen.getByLabelText('荷主'));
    await fillRequired(user);
    await user.selectOptions(screen.getByLabelText('貨物種別'), 'REFRIGERATED');
    await user.type(screen.getByLabelText('最低温度 (℃)'), '-25');
    await user.type(screen.getByLabelText('最高温度 (℃)'), '-18');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('予約一覧')).toBeInTheDocument()
    );

    expect(bookingApi.bookCargo).toHaveBeenCalledWith(
      expect.objectContaining({
        cargoType: 'REFRIGERATED',
        temperatureMinC: -25,
        temperatureMaxC: -18,
        hazardImoClass: null,
        hazardUnNumber: null,
        hazardDeclaration: null,
      })
    );
  });
});
