# frozen_string_literal: true

module Shipper
  module Domain
    # 法人荷主。Shipper のサブタイプ。契約番号と割引率を追加保持する。
    class CorporateShipper < Shipper
      attr_reader :contract_number, :discount_rate

      def self.register(contract_number:, discount_rate:, **shipper_attrs)
        raise ArgumentError, "法人荷主には契約番号が必須です" if contract_number.nil? || contract_number.to_s.strip.empty?
        raise ArgumentError, "法人荷主には割引率が必須です" if discount_rate.nil?

        # 個人向け register の不変条件も通す（superclass = Shipper::Domain::Shipper）
        shipper = superclass.register(**shipper_attrs, shipper_type: ShipperType.new(value: "CORPORATE"))
        new(contract_number: contract_number, discount_rate: discount_rate,
            code: shipper.code, name: shipper.name, email: shipper.email,
            phone: shipper.phone, address: shipper.address, shipper_type: shipper.shipper_type)
      end

      def initialize(contract_number:, discount_rate:, **shipper_attrs)
        super(**shipper_attrs)
        @contract_number = contract_number
        @discount_rate = discount_rate
      end
    end
  end
end
