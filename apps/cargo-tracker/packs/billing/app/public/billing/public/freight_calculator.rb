# frozen_string_literal: true

module Billing
  module Public
    # 料金計算の公開ファサード（他 BC 向け・US01 概算 / US21 本計算）。
    # FreightCalculationService を隠蔽し、プリミティブで受け渡す（BC 独立性・ACL）。
    class FreightCalculator
      # 計算結果の公開ビュー（金額は最小通貨単位 integer）。
      View = Data.define(:base_amount, :discounted_amount, :surcharge_amount, :tax_amount, :total_amount, :currency)

      def initialize(service: Domain::FreightCalculationService.new)
        @service = service
      end

      # 概算料金（割引なし・見積段階では荷主未確定）。US01。
      def estimate(distance_factor:, weight_kg:, cargo_type:, currency: "JPY")
        calculate(distance_factor: distance_factor, weight_kg: weight_kg, cargo_type: cargo_type,
                  discount_percentage: 0, surcharge_rates: {}, currency: currency)
      end

      # 本計算（割引・割増込み）。US21/US22。discount_percentage は 0〜30 の整数。
      # surcharge_rates 例: { "FUEL" => 0.05 }
      def calculate(distance_factor:, weight_kg:, cargo_type:, discount_percentage: 0,
                    surcharge_rates: {}, currency: "JPY")
        surcharges = surcharge_rates.map { |type, rate| Domain::Surcharge.new(type: type, rate: BigDecimal(rate.to_s)) }
        result = @service.calculate(
          distance_factor: BigDecimal(distance_factor.to_s), weight_kg: BigDecimal(weight_kg.to_s),
          cargo_type: cargo_type, discount_rate: Domain::DiscountRate.from_percentage(discount_percentage),
          surcharges: surcharges, currency: currency
        )
        to_view(result, currency)
      end

      private

      def to_view(result, currency)
        View.new(
          base_amount: result.base_amount.amount.to_i, discounted_amount: result.discounted_amount.amount.to_i,
          surcharge_amount: result.surcharge_amount.amount.to_i, tax_amount: result.tax_amount.amount.to_i,
          total_amount: result.total_amount.amount.to_i, currency: currency
        )
      end
    end
  end
end
