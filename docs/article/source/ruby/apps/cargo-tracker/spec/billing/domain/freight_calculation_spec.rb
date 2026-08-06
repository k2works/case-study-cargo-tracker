# frozen_string_literal: true

require "rails_helper"

# IT7: 料金計算のドメイン層（US21 料金算出・US22 法人割引）
RSpec.describe "Billing Context 料金計算ドメイン" do
  describe Billing::Domain::MoneyAmount do
    it "金額と通貨を保持し加算・乗算できる" do
      a = described_class.new(amount: 100_000, currency: "JPY")
      expect(a.amount).to eq(100_000)
      expect(a.currency).to eq("JPY")
      expect(a.add(described_class.new(amount: 9_000, currency: "JPY")).amount).to eq(109_000)
      expect(a.multiply(BigDecimal("1.10")).amount).to eq(110_000)
    end

    it "通貨が異なる加算は拒否する" do
      a = described_class.new(amount: 100, currency: "JPY")
      expect { a.add(described_class.new(amount: 1, currency: "USD")) }.to raise_error(ArgumentError)
    end

    it "値等価で比較できる" do
      expect(described_class.new(amount: 100, currency: "JPY")).to eq(described_class.new(amount: 100, currency: "JPY"))
    end
  end

  describe Billing::Domain::DiscountRate do
    it "0〜30% を許可する" do
      [ "0.0", "0.15", "0.30" ].each { |r| expect(described_class.new(rate: BigDecimal(r)).rate).to eq(BigDecimal(r)) }
    end

    it "境界外（負・30%超）は拒否する" do
      expect { described_class.new(rate: BigDecimal("-0.01")) }.to raise_error(ArgumentError)
      expect { described_class.new(rate: BigDecimal("0.31")) }.to raise_error(ArgumentError)
    end

    it "個人荷主向けのゼロ割引を生成できる" do
      expect(described_class.none.rate).to eq(BigDecimal("0"))
    end
  end

  describe Billing::Domain::Surcharge do
    it "基本料金への加算額を算出する（燃油サーチャージ）" do
      surcharge = described_class.new(type: "FUEL", rate: BigDecimal("0.05"))
      base = Billing::Domain::MoneyAmount.new(amount: 100_000, currency: "JPY")
      expect(surcharge.apply(base).amount).to eq(5_000)
    end
  end

  describe Billing::Domain::FreightCalculationService do
    subject(:service) { described_class.new }

    # 基本料金 = 距離係数 × 重量 × 貨物種別係数（GENERAL 1.0 / HAZARDOUS 1.8 / REFRIGERATED 1.5）
    # → 割引 → 割増 → 消費税 10%
    it "法人割引 10% と消費税 10% を正しく計算する（test_strategy の計算例）" do
      # 基本料金 100,000 円になるよう距離係数×重量を設定（GENERAL 係数 1.0）
      result = service.calculate(
        distance_factor: BigDecimal("100"), weight_kg: BigDecimal("1000"), cargo_type: "GENERAL",
        discount_rate: Billing::Domain::DiscountRate.new(rate: BigDecimal("0.10")), surcharges: []
      )
      # 割引後 90,000 円 × 消費税 10% = 99,000 円
      expect(result.base_amount.amount).to eq(100_000)
      expect(result.discounted_amount.amount).to eq(90_000)
      expect(result.tax_amount.amount).to eq(9_000)
      expect(result.total_amount.amount).to eq(99_000)
    end

    it "貨物種別係数を適用する（HAZARDOUS 1.8）" do
      result = service.calculate(
        distance_factor: BigDecimal("100"), weight_kg: BigDecimal("1000"), cargo_type: "HAZARDOUS",
        discount_rate: Billing::Domain::DiscountRate.none, surcharges: []
      )
      expect(result.base_amount.amount).to eq(180_000) # 100 × 1000 × 1.8
    end

    it "冷凍貨物係数を適用する（REFRIGERATED 1.5）" do
      result = service.calculate(
        distance_factor: BigDecimal("100"), weight_kg: BigDecimal("1000"), cargo_type: "REFRIGERATED",
        discount_rate: Billing::Domain::DiscountRate.none, surcharges: []
      )
      expect(result.base_amount.amount).to eq(150_000)
    end

    it "割引なし・燃油サーチャージ付きで計算する（割増は基本料金に加算）" do
      result = service.calculate(
        distance_factor: BigDecimal("100"), weight_kg: BigDecimal("1000"), cargo_type: "GENERAL",
        discount_rate: Billing::Domain::DiscountRate.none,
        surcharges: [ Billing::Domain::Surcharge.new(type: "FUEL", rate: BigDecimal("0.05")) ]
      )
      # 基本 100,000・割引0 → 100,000・燃油 5,000 加算 → 105,000・税 10,500 → 合計 115,500
      expect(result.surcharge_amount.amount).to eq(5_000)
      expect(result.total_amount.amount).to eq(115_500)
    end

    it "未知の貨物種別は拒否する" do
      expect {
        service.calculate(distance_factor: BigDecimal("1"), weight_kg: BigDecimal("1"), cargo_type: "UNKNOWN",
                          discount_rate: Billing::Domain::DiscountRate.none, surcharges: [])
      }.to raise_error(ArgumentError)
    end
  end
end
