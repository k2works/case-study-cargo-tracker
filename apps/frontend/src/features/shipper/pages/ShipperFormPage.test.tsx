import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as shipperApi from '../api/shipperApi';
import ShipperFormPage from './ShipperFormPage';

vi.mock('../api/shipperApi');

function renderNew() {
  return render(
    <MemoryRouter initialEntries={['/shippers/new']}>
      <Routes>
        <Route path="/shippers/new" element={<ShipperFormPage />} />
        <Route path="/shippers" element={<div>荷主一覧</div>} />
      </Routes>
    </MemoryRouter>
  );
}

async function fillRequired(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('氏名/社名'), '山田太郎');
  await user.type(screen.getByLabelText('住所 1 行目'), '東京都千代田区丸の内 1-1');
  await user.type(screen.getByLabelText('市区町村'), '千代田区');
  await user.type(screen.getByLabelText('国コード (ISO 3166-1)'), 'JP');
  await user.type(screen.getByLabelText('メールアドレス'), 'yamada@example.com');
  await user.type(screen.getByLabelText('電話番号'), '03-1234-5678');
}

describe('ShipperFormPage (新規登録)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('新規登録フォームが表示される', () => {
    renderNew();
    expect(screen.getByRole('heading', { name: '荷主新規登録' })).toBeInTheDocument();
    expect(screen.getByLabelText('氏名/社名')).toBeInTheDocument();
    expect(screen.getByLabelText('荷主種別')).toBeInTheDocument();
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
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([]);
    vi.mocked(shipperApi.registerShipper).mockResolvedValue({ shipperId: 'S-NEW' });
    renderNew();
    const user = userEvent.setup();

    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('荷主一覧')).toBeInTheDocument()
    );
  });

  it('既存メールアドレスがある場合は確認パネルが表示される', async () => {
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([
      {
        shipperId: 'S-EXISTING',
        shipperType: 'INDIVIDUAL',
        name: '既存荷主',
        addressLine1: '既存住所',
        addressLine2: null,
        city: '東京',
        countryCode: 'JP',
        postalCode: null,
        email: 'yamada@example.com',
        phone: '03-0000-0000',
        contractNumber: null,
        discountRate: null,
        active: true,
      },
    ]);
    renderNew();
    const user = userEvent.setup();

    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText(/既存荷主が見つかりました/)).toBeInTheDocument()
    );
    expect(screen.getByText('S-EXISTING')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'それでも新規登録する' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '既存荷主を使用する' })).toBeInTheDocument();
  });

  it('既存荷主確認後に「それでも新規登録する」で登録が完了する', async () => {
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([
      {
        shipperId: 'S-EXISTING',
        shipperType: 'INDIVIDUAL',
        name: '既存荷主',
        addressLine1: '既存住所',
        addressLine2: null,
        city: '東京',
        countryCode: 'JP',
        postalCode: null,
        email: 'yamada@example.com',
        phone: '03-0000-0000',
        contractNumber: null,
        discountRate: null,
        active: true,
      },
    ]);
    vi.mocked(shipperApi.registerShipper).mockResolvedValue({ shipperId: 'S-NEW' });
    renderNew();
    const user = userEvent.setup();

    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));
    await waitFor(() => screen.getByText(/既存荷主が見つかりました/));
    await user.click(screen.getByRole('button', { name: 'それでも新規登録する' }));

    await waitFor(() =>
      expect(screen.getByText('荷主一覧')).toBeInTheDocument()
    );
  });

  it('「既存荷主を使用する」を押すと一覧ページへ戻る', async () => {
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([
      {
        shipperId: 'S-EXISTING',
        shipperType: 'INDIVIDUAL',
        name: '既存荷主',
        addressLine1: '既存住所',
        addressLine2: null,
        city: '東京',
        countryCode: 'JP',
        postalCode: null,
        email: 'yamada@example.com',
        phone: '03-0000-0000',
        contractNumber: null,
        discountRate: null,
        active: true,
      },
    ]);
    renderNew();
    const user = userEvent.setup();

    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));
    await waitFor(() => screen.getByText(/既存荷主が見つかりました/));
    await user.click(screen.getByRole('button', { name: '既存荷主を使用する' }));

    await waitFor(() =>
      expect(screen.getByText('荷主一覧')).toBeInTheDocument()
    );
    expect(shipperApi.registerShipper).not.toHaveBeenCalled();
  });
});

describe('ShipperFormPage (US03 法人荷主)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('INDIVIDUAL では契約番号フィールドが表示されない', () => {
    renderNew();
    expect(screen.queryByLabelText('契約番号')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('割引率 (%) 0〜30')).not.toBeInTheDocument();
  });

  it('CORPORATE 選択で契約番号と割引率フィールドが表示される', async () => {
    renderNew();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('荷主種別'), 'CORPORATE');

    expect(screen.getByLabelText('契約番号')).toBeInTheDocument();
    expect(screen.getByLabelText('割引率 (%) 0〜30')).toBeInTheDocument();
  });

  it('CORPORATE で契約番号が未入力ならエラー', async () => {
    renderNew();
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText('荷主種別'), 'CORPORATE');
    await fillRequired(user);
    await user.type(screen.getByLabelText('割引率 (%) 0〜30'), '10');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('契約番号は必須')
    );
  });

  it('CORPORATE で割引率が 30 超ならエラー', async () => {
    renderNew();
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText('荷主種別'), 'CORPORATE');
    await fillRequired(user);
    await user.type(screen.getByLabelText('契約番号'), 'CONTRACT-2026-001');
    await user.type(screen.getByLabelText('割引率 (%) 0〜30'), '31');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('0〜30')
    );
  });

  it('CORPORATE で正常登録すると discountRate が 0.0-0.3 に変換され送信される', async () => {
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([]);
    vi.mocked(shipperApi.registerShipper).mockResolvedValue({ shipperId: 'S-NEW' });
    renderNew();
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('荷主種別'), 'CORPORATE');
    await fillRequired(user);
    await user.type(screen.getByLabelText('契約番号'), 'CONTRACT-2026-001');
    await user.type(screen.getByLabelText('割引率 (%) 0〜30'), '15');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('荷主一覧')).toBeInTheDocument()
    );

    expect(shipperApi.registerShipper).toHaveBeenCalledWith(
      expect.objectContaining({
        shipperType: 'CORPORATE',
        contractNumber: 'CONTRACT-2026-001',
        discountRate: 0.15,
      })
    );
  });

  it('INDIVIDUAL では contractNumber / discountRate に null が送信される', async () => {
    vi.mocked(shipperApi.fetchShippersByEmail).mockResolvedValue([]);
    vi.mocked(shipperApi.registerShipper).mockResolvedValue({ shipperId: 'S-NEW' });
    renderNew();
    const user = userEvent.setup();

    await fillRequired(user);
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() =>
      expect(screen.getByText('荷主一覧')).toBeInTheDocument()
    );

    expect(shipperApi.registerShipper).toHaveBeenCalledWith(
      expect.objectContaining({
        shipperType: 'INDIVIDUAL',
        contractNumber: null,
        discountRate: null,
      })
    );
  });
});
