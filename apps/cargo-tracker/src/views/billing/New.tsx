import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';

/** 料金算出画面に表示する輸送実績・料金プレビュー（US21/US22） */
export interface InvoiceDraft {
  bookingId: string;
  shipperName: string;
  shipperType: string;
  isCorporate: boolean;
  origin: string;
  destination: string;
  weightKg: number;
  cargoType: string;
  transitDays: number;
  baseAmountValue: number;
  discountRatePercent: string;
  discountAmountValue: number;
  finalPreviewValue: number;
  currency: string;
  activeExceptions: readonly string[];
}

interface BillingNewProps {
  user: AuthenticatedUser;
  draft: InvoiceDraft;
  csrfToken?: string;
  error?: string;
}

/** 数字の行数（例外調整の入力枠。US21-6） */
const ADJUSTMENT_ROWS = [0, 1];

/**
 * 料金算出画面（/billing/invoices/new?bookingId=…・US21/US22）。
 * 輸送実績・基本料金・（法人なら）割引根拠を表示し、例外調整（減額・補償費用）を入力して発行する。
 */
export function BillingNew({ user, draft, csrfToken, error }: BillingNewProps): ReactElement {
  return (
    <Layout title="料金算出" user={user} activePath="/billing/invoices" csrfToken={csrfToken}>
      <h1 className="h3 mb-3" data-testid="billing-new-heading">
        料金算出
      </h1>
      {error && (
        <div className="alert alert-danger" role="alert" data-testid="billing-error">
          {error}
        </div>
      )}

      <section className="mb-4">
        <h2 className="h5">輸送実績</h2>
        <table className="table table-sm" data-testid="billing-performance">
          <tbody>
            <tr>
              <th>予約 ID</th>
              <td>{draft.bookingId}</td>
            </tr>
            <tr>
              <th>荷主</th>
              <td>
                {draft.shipperName}（{draft.isCorporate ? '法人' : '個人'}）
              </td>
            </tr>
            <tr>
              <th>区間</th>
              <td>
                {draft.origin} → {draft.destination}
              </td>
            </tr>
            <tr>
              <th>所要日数</th>
              <td>{draft.transitDays} 日</td>
            </tr>
            <tr>
              <th>重量</th>
              <td>{draft.weightKg} kg</td>
            </tr>
            <tr>
              <th>貨物種別</th>
              <td>{draft.cargoType}</td>
            </tr>
            <tr>
              <th>未解決の例外</th>
              <td>{draft.activeExceptions.length === 0 ? 'なし' : draft.activeExceptions.join('、')}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section className="mb-4">
        <h2 className="h5">料金（割引根拠）</h2>
        <table className="table table-sm" data-testid="billing-calculation">
          <tbody>
            <tr>
              <th>基本料金</th>
              <td>
                {draft.baseAmountValue.toLocaleString()} {draft.currency}
              </td>
            </tr>
            {draft.isCorporate && (
              <tr>
                <th>法人割引</th>
                <td>
                  割引率 {draft.discountRatePercent}%（−{draft.discountAmountValue.toLocaleString()} {draft.currency}）
                </td>
              </tr>
            )}
            <tr>
              <th>請求金額（税込・調整前）</th>
              <td data-testid="billing-final-preview">
                {draft.finalPreviewValue.toLocaleString()} {draft.currency}
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <form action="/billing/invoices" method="post" className="col-md-8" data-testid="billing-form">
        {csrfToken !== undefined && <input type="hidden" name="_csrf" value={csrfToken} />}
        <input type="hidden" name="bookingId" value={draft.bookingId} />
        <h2 className="h5">料金調整（例外発生時・任意）</h2>
        {ADJUSTMENT_ROWS.map((row) => (
          <div className="row g-2 align-items-end mb-2" key={row}>
            <div className="col-md-5">
              <label className="form-label" htmlFor={`adjustmentDescription-${row}`}>
                説明
              </label>
              <input
                type="text"
                className="form-control"
                id={`adjustmentDescription-${row}`}
                name="adjustmentDescription[]"
                placeholder="遅延補償 / 破損減額 など"
              />
            </div>
            <div className="col-md-3">
              <label className="form-label" htmlFor={`adjustmentAmount-${row}`}>
                金額
              </label>
              <input
                type="number"
                className="form-control"
                id={`adjustmentAmount-${row}`}
                name="adjustmentAmount[]"
                min="0"
              />
            </div>
            <div className="col-md-4">
              <label className="form-label" htmlFor={`adjustmentDeduction-${row}`}>
                区分
              </label>
              {/* select は常に値を送るため、加算・減額の行数と配列添字が確実に揃う */}
              <select
                className="form-select"
                id={`adjustmentDeduction-${row}`}
                name="adjustmentDeduction[]"
                defaultValue="false"
              >
                <option value="false">加算（補償費用）</option>
                <option value="true">減額（控除）</option>
              </select>
            </div>
          </div>
        ))}
        <div className="mt-3">
          <button type="submit" className="btn btn-primary" data-testid="billing-submit">
            請求書を発行
          </button>
          <a href="/billing/invoices" className="btn btn-outline-secondary ms-2">
            一覧に戻る
          </a>
        </div>
      </form>
    </Layout>
  );
}
