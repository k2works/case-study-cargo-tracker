import type { ReactElement } from 'react';

interface CorporateFieldsProps {
  contractNumber?: string;
  discountRatePercent?: number;
}

/**
 * 法人契約フィールド（契約番号・割引率）。
 * 荷主種別「法人」選択時に htmx で差し替え表示する。個人時は空フラグメントを返す。
 */
export function CorporateFields({
  contractNumber,
  discountRatePercent,
}: CorporateFieldsProps): ReactElement {
  return (
    <div id="corporate-fields" data-testid="corporate-fields">
      <div className="mb-3">
        <label htmlFor="contractNumber" className="form-label">
          契約番号
        </label>
        <input
          type="text"
          className="form-control"
          id="contractNumber"
          name="contractNumber"
          defaultValue={contractNumber ?? ''}
          required
        />
      </div>
      <div className="mb-3">
        <label htmlFor="discountRate" className="form-label">
          割引率（%）
        </label>
        <input
          type="number"
          className="form-control"
          id="discountRate"
          name="discountRate"
          min={0}
          max={30}
          step={1}
          defaultValue={discountRatePercent ?? 0}
          required
        />
        <div className="form-text">0〜30% の範囲で指定してください。</div>
      </div>
    </div>
  );
}

/** 個人選択時の空フラグメント（htmx で法人フィールドを消す） */
export function EmptyCorporateFields(): ReactElement {
  return <div id="corporate-fields" data-testid="corporate-fields" />;
}
