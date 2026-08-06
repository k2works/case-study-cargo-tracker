import type { ReactElement } from 'react';
import type { LocationOption } from '../../shared/infrastructure/database/location-query.js';

/**
 * UN/LOCODE 入力の候補を提供する datalist。
 * 出発地・目的地テキスト入力に `list="unlocodes"` を付けて発見可能性を高める。
 */
export function LocationDatalist({ locations }: { locations: LocationOption[] }): ReactElement {
  return (
    <datalist id="unlocodes">
      {locations.map((l) => (
        <option key={l.unlocode} value={l.unlocode}>
          {l.name}
        </option>
      ))}
    </datalist>
  );
}
