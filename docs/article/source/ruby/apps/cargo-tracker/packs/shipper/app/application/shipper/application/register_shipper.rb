# frozen_string_literal: true

module Shipper
  module Application
    # 荷主登録ユースケース（US02/US03 / RegisterShipperCommand）。
    # メール重複チェック → 集約生成 → 永続化。
    class RegisterShipper
      # 登録結果を表す値オブジェクト。
      Result = Struct.new(:shipper, :existing_shipper, :error_message, keyword_init: true) do
        def success?
          shipper.present? && error_message.nil? && existing_shipper.nil?
        end

        def duplicate?
          existing_shipper.present?
        end
      end

      # リポジトリポート（`Shipper::Domain::ShipperRepository`）を注入する。
      # 具象の組み立ては合成ルート（コントローラ等）で行う（DIP）。
      def initialize(repository:)
        @repository = repository
      end

      def call(shipper_type:, name:, email:, address: nil, phone: nil,
               contract_number: nil, discount_rate: nil)
        existing = @repository.find_by_email(email)
        return Result.new(existing_shipper: existing) if existing

        shipper = build(shipper_type: shipper_type, name: name, email: email, address: address,
                        phone: phone, contract_number: contract_number, discount_rate: discount_rate)
        @repository.save(shipper)
        Result.new(shipper: shipper)
      rescue ActiveRecord::RecordNotUnique
        # 同時登録による競合。DB の一意制約で弾かれたら重複として扱う。
        Result.new(existing_shipper: @repository.find_by_email(email))
      rescue ArgumentError => e
        Result.new(error_message: e.message)
      end

      private

      def build(shipper_type:, name:, email:, address:, phone:, contract_number:, discount_rate:)
        base = {
          code: Domain::ShipperCode.generate,
          name: name,
          email: email,
          phone: phone,
          address: (Domain::Address.new(value: address) if address.present?)
        }

        if shipper_type == "CORPORATE"
          raise ArgumentError, "法人荷主には割引率が必須です" if discount_rate.nil? || discount_rate.to_s.strip.empty?

          Domain::CorporateShipper.register(
            **base,
            contract_number: contract_number,
            discount_rate: Domain::DiscountRate.new(value: discount_rate)
          )
        else
          Domain::Shipper.register(**base, shipper_type: Domain::ShipperType.new(value: "INDIVIDUAL"))
        end
      end
    end
  end
end
