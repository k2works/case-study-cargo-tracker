# frozen_string_literal: true

module Shipper
  module Public
    # 荷主登録の公開ファサード（アプリ層＝合成ルート向け）。
    # 内部のアプリケーションサービス・リポジトリを隠蔽し、登録結果に
    # 越境識別子 shippers.id を返す（ADR-0003）。
    class ShipperRegistration
      Result = Struct.new(:shipper_id, :shipper_code, :existing, :error_message, keyword_init: true) do
        def success?
          shipper_id.present? && error_message.nil? && existing.nil?
        end

        def duplicate?
          existing.present?
        end
      end

      def initialize(repository: Infrastructure::ActiveRecordShipperRepository.new)
        @repository = repository
      end

      def call(**params)
        result = Application::RegisterShipper.new(repository: @repository).call(**params)

        if result.success?
          record = Infrastructure::ShipperRecord.find_by!(shipper_code: result.shipper.code.value)
          Result.new(shipper_id: record.id, shipper_code: record.shipper_code)
        elsif result.duplicate?
          Result.new(existing: existing_view(result.existing_shipper))
        else
          Result.new(error_message: result.error_message)
        end
      end

      private

      def existing_view(shipper)
        record = Infrastructure::ShipperRecord.find_by(email: shipper.email)
        corporate = record.shipper_type == "CORPORATE"
        ShipperDirectory::View.new(id: record.id, code: record.shipper_code, name: record.name,
                                   email: record.email, corporate: corporate,
                                   discount_percentage: (corporate ? (record.discount_rate * 100).to_i : nil))
      end
    end
  end
end
