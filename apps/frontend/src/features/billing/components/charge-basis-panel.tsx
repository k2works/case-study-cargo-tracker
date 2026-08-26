import { formatYen } from '../money'
import type { ChargeBasis, Money } from '../types'

/**
 * 基本料金の根拠（[ADR-027] 決定 1）。
 *
 * <p><strong>金額そのものより「なぜその金額か」が読めることを優先する。</strong>
 * 経理担当者は請求の根拠を荷主に説明する。金額だけを出すと、聞かれたときに答えられない。
 *
 * <p><strong>距離は持っていない</strong>ことを画面が言う。港のマスタに緯度経度が無く、
 * 航海も距離を持たない。区間数で代替していることを黙っていると、
 * <strong>受入基準どおりに距離で計算していると読まれる</strong>。
 */
export function ChargeBasisPanel({
  basis,
  baseAmount,
}: Readonly<{
  basis: ChargeBasis & { cargoTypeLabel?: string }
  baseAmount: Money
}>) {
  return (
    <section aria-labelledby="basis-heading" className="space-y-2">
      <h2 id="basis-heading" className="text-lg font-semibold">
        輸送実績と基本料金
      </h2>

      <dl
        className="grid grid-cols-2 gap-2 rounded border border-gray-200 p-4 md:grid-cols-4"
        data-testid="charge-basis"
      >
        <div>
          <dt className="text-sm text-gray-600">基準運賃</dt>
          <dd>{formatYen(basis.baseFare)}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">区間数</dt>
          <dd>{basis.legCount} 区間</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">区間係数</dt>
          <dd>× {basis.legFactor}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">重量</dt>
          <dd>{basis.weightKg.toLocaleString('ja-JP')} kg</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">重量係数</dt>
          <dd>× {basis.weightFactor}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">貨物種別</dt>
          <dd>{basis.cargoTypeLabel ?? basis.cargoType}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">貨物種別係数</dt>
          <dd>× {basis.cargoTypeFactor}</dd>
        </div>
        <div>
          <dt className="text-sm text-gray-600">基本料金</dt>
          <dd className="font-bold">{formatYen(baseAmount)}</dd>
        </div>
      </dl>

      <p className="text-sm text-gray-600">
        {/* **代用が粗いことは書ける。根拠が無いことは書けない**（[ADR-027] 決定 1） */}
        {/* **改行を空白にしない。** JSX の改行はそのまま半角空白になり、
            句点の直後に空きができる（キャプチャで気づいた） */}
        輸送距離は保持していないため、<strong>区間数で代替</strong>
        {'しています。東京 → 横浜と東京 → ロサンゼルスは、どちらも 1 区間として扱われます。'}
      </p>
    </section>
  )
}
