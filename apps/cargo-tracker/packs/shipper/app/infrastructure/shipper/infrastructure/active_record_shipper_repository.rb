# frozen_string_literal: true

module Shipper
  module Infrastructure
    # 荷主リポジトリの Active Record 実装（ヘキサゴナルの出力アダプタ）。
    # ドメイン集約（PORO）と ShipperRecord（AR）の相互変換を担う。
    class ActiveRecordShipperRepository
      # 集約を永続化する。
      def save(shipper)
        record = ShipperRecord.find_or_initialize_by(shipper_code: shipper.code.value)
        record.assign_attributes(to_columns(shipper))
        record.save!
        shipper
      end

      def find_by_code(code)
        record = ShipperRecord.find_by(shipper_code: code.value)
        record && to_domain(record)
      end

      def find_by_email(email)
        record = ShipperRecord.find_by(email: email)
        record && to_domain(record)
      end

      def exists_by_email?(email)
        ShipperRecord.exists?(email: email)
      end

      def all
        ShipperRecord.order(:created_at).map { |r| to_domain(r) }
      end

      private

      def to_columns(shipper)
        columns = {
          shipper_code: shipper.code.value,
          shipper_type: shipper.shipper_type.value,
          name: shipper.name,
          address: shipper.address&.value,
          email: shipper.email,
          phone: shipper.phone
        }
        if shipper.corporate?
          columns[:contract_number] = shipper.contract_number
          columns[:discount_rate] = shipper.discount_rate.value
        end
        columns
      end

      def to_domain(record)
        base = {
          code: Domain::ShipperCode.new(value: record.shipper_code),
          name: record.name,
          email: record.email,
          phone: record.phone,
          address: (Domain::Address.new(value: record.address) if record.address)
        }

        if record.shipper_type == "CORPORATE"
          Domain::CorporateShipper.new(
            **base,
            shipper_type: Domain::ShipperType.new(value: "CORPORATE"),
            contract_number: record.contract_number,
            discount_rate: Domain::DiscountRate.new(value: record.discount_rate)
          )
        else
          Domain::Shipper.new(**base, shipper_type: Domain::ShipperType.new(value: record.shipper_type))
        end
      end
    end
  end
end
