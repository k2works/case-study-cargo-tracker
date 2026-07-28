# frozen_string_literal: true

module Routing
  module Application
    # 航海スケジュール登録ユースケース（US24 / RegisterVoyageCommand）。
    class RegisterVoyage
      Result = Struct.new(:voyage_number, :error_message, keyword_init: true) do
        def success?
          voyage_number.present? && error_message.nil?
        end
      end

      def initialize(repository:)
        @repository = repository
      end

      def call(voyage_number:, carrier_name:, supported_cargo_types:, movements:, ship_name: nil)
        if @repository.exists_by_number?(voyage_number)
          return Result.new(error_message: "航海番号 #{voyage_number} は既に登録されています")
        end

        voyage = Domain::Voyage.register(
          voyage_number: voyage_number, carrier_name: carrier_name, ship_name: ship_name,
          supported_cargo_types: supported_cargo_types,
          movements: MovementBuilder.build(movements)
        )
        @repository.save(voyage)
        Result.new(voyage_number: voyage.voyage_number.value)
      rescue ArgumentError => e
        Result.new(error_message: e.message)
      end
    end
  end
end
