import type { ReactElement } from 'react';
import { CargoType } from '../../shared/domain/model/cargo-type.js';

/**
 * 貨物種別に応じた条件付きフィールド（危険物申告 / 温度管理条件）。
 * 種別選択時に htmx で差し替える。US05。
 */
export function CargoTypeFields({ cargoType }: { cargoType?: string }): ReactElement {
  if (cargoType === CargoType.HAZARDOUS) {
    return (
      <div id="cargo-type-fields" data-testid="cargo-type-fields">
        <div className="mb-3">
          <label htmlFor="hazardousClass" className="form-label">危険物クラス</label>
          <input type="text" className="form-control" id="hazardousClass" name="hazardousClass" required />
        </div>
        <div className="mb-3">
          <label htmlFor="unNumber" className="form-label">UN 番号</label>
          <input type="text" className="form-control" id="unNumber" name="unNumber" required />
        </div>
        <div className="mb-3">
          <label htmlFor="properShippingName" className="form-label">正式輸送品名</label>
          <input type="text" className="form-control" id="properShippingName" name="properShippingName" required />
        </div>
      </div>
    );
  }
  if (cargoType === CargoType.REFRIGERATED) {
    return (
      <div id="cargo-type-fields" data-testid="cargo-type-fields">
        <div className="mb-3">
          <label htmlFor="minTemperature" className="form-label">最低温度</label>
          <input type="number" step="0.1" className="form-control" id="minTemperature" name="minTemperature" required />
        </div>
        <div className="mb-3">
          <label htmlFor="maxTemperature" className="form-label">最高温度</label>
          <input type="number" step="0.1" className="form-control" id="maxTemperature" name="maxTemperature" required />
        </div>
        <div className="mb-3">
          <label htmlFor="temperatureUnit" className="form-label">温度単位</label>
          <select className="form-select" id="temperatureUnit" name="temperatureUnit">
            <option value="CELSIUS">摂氏（℃）</option>
            <option value="FAHRENHEIT">華氏（℉）</option>
          </select>
        </div>
      </div>
    );
  }
  return <div id="cargo-type-fields" data-testid="cargo-type-fields" />;
}
