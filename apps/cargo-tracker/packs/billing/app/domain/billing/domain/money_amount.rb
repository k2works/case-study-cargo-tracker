# frozen_string_literal: true

module Billing
  module Domain
    # 金額（MoneyAmount）。金額と通貨コードを保持する不変の値オブジェクト。
    # 丸め誤差を避けるため BigDecimal で保持する（ユビキタス言語＝MoneyAmount・ADR/設計反映 IT7）。
    class MoneyAmount
      attr_reader :amount, :currency

      def self.zero(currency: "JPY") = new(amount: 0, currency: currency)

      def initialize(amount:, currency: "JPY")
        @amount = amount.is_a?(BigDecimal) ? amount : BigDecimal(amount.to_s)
        @currency = currency
        freeze
      end

      def add(other)
        raise ArgumentError, "通貨が異なります: #{currency} vs #{other.currency}" unless currency == other.currency

        self.class.new(amount: amount + other.amount, currency: currency)
      end

      # 係数を掛ける（割引・消費税など）。
      def multiply(factor)
        self.class.new(amount: amount * BigDecimal(factor.to_s), currency: currency)
      end

      def ==(other) = other.is_a?(MoneyAmount) && other.amount == amount && other.currency == currency
      alias eql? ==
      def hash = [ amount, currency ].hash
      def to_s = "#{amount.to_i} #{currency}"
    end
  end
end
