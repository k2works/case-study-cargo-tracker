/**
 * 貨物種別（共有カーネル）。Booking / Estimation で同一の値を使用する。
 */
export const CargoType = {
  GENERAL: 'GENERAL',
  HAZARDOUS: 'HAZARDOUS',
  REFRIGERATED: 'REFRIGERATED',
} as const;
export type CargoType = (typeof CargoType)[keyof typeof CargoType];

export const CARGO_TYPE_LABELS: Record<CargoType, string> = {
  GENERAL: '一般貨物',
  HAZARDOUS: '危険物',
  REFRIGERATED: '冷凍・冷蔵貨物',
};

export function isCargoType(value: string): value is CargoType {
  return value === 'GENERAL' || value === 'HAZARDOUS' || value === 'REFRIGERATED';
}
