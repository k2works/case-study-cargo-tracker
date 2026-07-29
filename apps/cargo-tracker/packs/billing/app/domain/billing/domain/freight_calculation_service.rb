# frozen_string_literal: true

module Billing
  module Domain
    # 輸送料金計算のドメインサービス（US21 料金算出・US22 法人割引）。
    # 基本料金 = 距離係数 × 重量 × 貨物種別係数 → 割引 → 割増加算 → 消費税 10%。
    # Invoice 集約から独立し、US01 概算・US21 本計算の両方で使用する。
    # 貨物種別係数は Billing 内に閉じ、他 BC の CargoType に依存しない（BC 独立性）。
    class FreightCalculationService
      TAX_RATE = BigDecimal("0.10") # 消費税 10%
      # 貨物種別係数（GENERAL 一般 / HAZARDOUS 危険物 / REFRIGERATED 冷凍・冷蔵）。
      CARGO_TYPE_FACTORS = {
        "GENERAL" => BigDecimal("1.0"),
        "HAZARDOUS" => BigDecimal("1.8"),
        "REFRIGERATED" => BigDecimal("1.5")
      }.freeze

      # 計算結果（基本・割引後・割増額・税額・合計）を保持する不変オブジェクト。
      Result = Data.define(:base_amount, :discounted_amount, :surcharge_amount, :tax_amount, :total_amount)

      def calculate(distance_factor:, weight_kg:, cargo_type:, discount_rate:, surcharges: [], currency: "JPY")
        factor = CARGO_TYPE_FACTORS[cargo_type]
        raise ArgumentError, "貨物種別が不正です: #{cargo_type}" if factor.nil?

        base = MoneyAmount.new(amount: BigDecimal(distance_factor.to_s) * BigDecimal(weight_kg.to_s) * factor,
                               currency: currency)
        discounted = base.multiply(discount_rate.remaining_factor)
        surcharge_total = surcharges.reduce(MoneyAmount.zero(currency: currency)) do |sum, s|
          sum.add(s.apply(base))
        end
        surcharged = discounted.add(surcharge_total)
        tax = surcharged.multiply(TAX_RATE)
        total = surcharged.add(tax)

        Result.new(base_amount: base, discounted_amount: discounted, surcharge_amount: surcharge_total,
                   tax_amount: tax, total_amount: total)
      end
    end
  end
end
