import { Decimal } from 'decimal.js';
import { Money } from './money.js';
import { BillingValidationError } from './billing-validation-error.js';

/** 基本通貨。国内輸送を前提に JPY 固定（最小通貨単位＝1 円） */
const BASE_CURRENCY = 'JPY';

/**
 * 距離係数の単価（円/日）。
 * domain-model の料金式「距離係数 × 重量 × 貨物種別係数」の距離係数は設計未定義のため、
 * IT7 では旅程の所要日数（transitDays）× 単価で導出する簡易式とする（iteration_plan-7 注 3）。
 * 精緻化（港間距離マスタ等）は運用フェーズの判断とする。
 */
const DISTANCE_UNIT_PRICE_PER_DAY = new Decimal(100);

/** 貨物種別係数（domain-model 料金計算ロジック） */
const CARGO_TYPE_COEFFICIENTS: Record<string, Decimal> = {
  GENERAL: new Decimal('1.0'),
  HAZARDOUS: new Decimal('1.8'),
  REFRIGERATED: new Decimal('1.5'),
};

interface CalculateParams {
  /** 旅程の所要日数（最初の load から最後の unload まで） */
  transitDays: number;
  /** 貨物重量（kg） */
  weightKg: Decimal;
  /** 貨物種別（GENERAL / HAZARDOUS / REFRIGERATED） */
  cargoType: string;
}

/**
 * 料金計算ドメインサービス。基本料金を算出する。
 * 基本料金 = 距離係数（transitDays × 単価） × 重量（kg） × 貨物種別係数
 */
export class FreightCalculator {
  calculate(params: CalculateParams): Money {
    if (params.transitDays <= 0) {
      throw new BillingValidationError(`所要日数は正の値が必要です: ${params.transitDays}`);
    }
    if (params.weightKg.lessThanOrEqualTo(0)) {
      throw new BillingValidationError(`重量は正の値が必要です: ${params.weightKg.toString()}`);
    }
    const coefficient = CARGO_TYPE_COEFFICIENTS[params.cargoType];
    if (coefficient === undefined) {
      throw new BillingValidationError(`不正な貨物種別: ${params.cargoType}`);
    }
    const distanceFactor = DISTANCE_UNIT_PRICE_PER_DAY.times(params.transitDays);
    const base = distanceFactor.times(params.weightKg).times(coefficient);
    return Money.of(base, BASE_CURRENCY);
  }
}
