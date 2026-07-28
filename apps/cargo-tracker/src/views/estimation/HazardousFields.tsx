import type { ReactElement } from 'react';

/**
 * 危険物申告フィールド（クラス・UN 番号・正式輸送品名）。
 * 貨物種別「危険物」選択時に htmx で差し替え表示する。
 */
export function HazardousFields(): ReactElement {
  return (
    <div id="hazardous-fields" data-testid="hazardous-fields">
      <div className="mb-3">
        <label htmlFor="hazardousClass" className="form-label">
          危険物クラス
        </label>
        <input type="text" className="form-control" id="hazardousClass" name="hazardousClass" required />
      </div>
      <div className="mb-3">
        <label htmlFor="unNumber" className="form-label">
          UN 番号
        </label>
        <input type="text" className="form-control" id="unNumber" name="unNumber" required />
      </div>
      <div className="mb-3">
        <label htmlFor="properShippingName" className="form-label">
          正式輸送品名
        </label>
        <input
          type="text"
          className="form-control"
          id="properShippingName"
          name="properShippingName"
          required
        />
      </div>
    </div>
  );
}

/** 危険物以外選択時の空フラグメント */
export function EmptyHazardousFields(): ReactElement {
  return <div id="hazardous-fields" data-testid="hazardous-fields" />;
}
