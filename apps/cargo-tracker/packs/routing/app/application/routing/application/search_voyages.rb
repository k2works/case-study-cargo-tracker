# frozen_string_literal: true

module Routing
  module Application
    # 航海スケジュール検索（US07 / クエリサービス）。
    # 出発地・目的地・出発期間・貨物種別で利用可能な航海を絞り込む。
    class SearchVoyages
      # 検索結果の投影（航海番号・運送会社・出発/到着日・経由港）。
      Result = Data.define(:voyage_number, :carrier_name, :ship_name,
                           :departure_date, :arrival_date, :ports, :supported_cargo_types) do
        def origin = ports.first
        def destination = ports.last
      end

      def initialize(repository:)
        @repository = repository
      end

      def call(origin: nil, destination: nil, departure_from: nil, departure_to: nil, cargo_type: nil)
        from = parse_date(departure_from)
        to = parse_date(departure_to)

        @repository.all.filter_map do |voyage|
          next unless origin.blank? || voyage.origin == origin
          next unless destination.blank? || voyage.destination == destination
          next if from && voyage.schedule.departure_date < from.beginning_of_day
          next if to && voyage.schedule.departure_date > to.end_of_day
          next if cargo_type.present? && !voyage.supports?(cargo_type)

          to_result(voyage)
        end
      end

      private

      def parse_date(value)
        return nil if value.blank?

        Date.parse(value.to_s)
      rescue ArgumentError
        nil
      end

      def to_result(voyage)
        Result.new(
          voyage_number: voyage.voyage_number.value, carrier_name: voyage.carrier_name,
          ship_name: voyage.ship_name, departure_date: voyage.schedule.departure_date,
          arrival_date: voyage.schedule.arrival_date, ports: voyage.ports,
          supported_cargo_types: voyage.supported_cargo_types
        )
      end
    end
  end
end
