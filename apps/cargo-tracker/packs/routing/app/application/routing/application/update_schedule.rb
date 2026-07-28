# frozen_string_literal: true

module Routing
  module Application
    # 航海スケジュール更新ユースケース（US25 / UpdateScheduleCommand）。
    # 既存航海を呼び出し、スケジュールを差し替えて上書き保存する。
    class UpdateSchedule
      Result = Struct.new(:voyage_number, :error_message, keyword_init: true) do
        def success?
          voyage_number.present? && error_message.nil?
        end
      end

      def initialize(repository:)
        @repository = repository
      end

      def call(voyage_number:, movements:, carrier_name: nil, ship_name: nil, supported_cargo_types: nil)
        voyage = @repository.find_by_number(Domain::VoyageNumber.new(value: voyage_number))
        return Result.new(error_message: "航海番号 #{voyage_number} は存在しません") if voyage.nil?

        # スケジュール差し替え（属性変更は再登録で表現）
        updated = Domain::Voyage.register(
          voyage_number: voyage_number,
          carrier_name: carrier_name.presence || voyage.carrier_name,
          ship_name: ship_name.presence || voyage.ship_name,
          supported_cargo_types: supported_cargo_types.presence || voyage.supported_cargo_types,
          movements: MovementBuilder.build(movements)
        )
        @repository.save(updated)
        Result.new(voyage_number: voyage_number)
      rescue ArgumentError => e
        Result.new(error_message: e.message)
      end
    end
  end
end
