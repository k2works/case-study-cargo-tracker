# frozen_string_literal: true

module Routing
  module Domain
    # 航海集約ルート（PORO）。航路スケジュールを管理する。
    class Voyage
      CARGO_TYPES = %w[GENERAL HAZARDOUS REFRIGERATED].freeze

      attr_reader :voyage_number, :carrier_name, :ship_name, :supported_cargo_types, :schedule

      # 新規航海を登録する（US24）。
      def self.register(voyage_number:, carrier_name:, supported_cargo_types:, movements:, ship_name: nil)
        raise ArgumentError, "運送会社は必須です" if carrier_name.nil? || carrier_name.to_s.strip.empty?

        types = normalize_types(supported_cargo_types)
        new(
          voyage_number: VoyageNumber.new(value: voyage_number),
          carrier_name: carrier_name, ship_name: ship_name,
          supported_cargo_types: types, schedule: Schedule.new(movements)
        )
      end

      def self.normalize_types(types)
        list = Array(types).map(&:to_s).map(&:upcase).uniq
        list = %w[GENERAL] if list.empty?
        invalid = list - CARGO_TYPES
        raise ArgumentError, "対応貨物種別が不正です: #{invalid.join(',')}" if invalid.any?

        list
      end
      private_class_method :normalize_types

      def initialize(voyage_number:, carrier_name:, supported_cargo_types:, schedule:, ship_name: nil)
        @voyage_number = voyage_number
        @carrier_name = carrier_name
        @ship_name = ship_name
        @supported_cargo_types = supported_cargo_types
        @schedule = schedule
      end

      # スケジュールを差し替える（US25）。
      def update_schedule(movements)
        @schedule = Schedule.new(movements)
      end

      def origin = schedule.origin
      def destination = schedule.destination
      def ports = schedule.ports

      # 指定貨物種別に対応しているか（US07 絞り込み）。
      def supports?(cargo_type)
        supported_cargo_types.include?(cargo_type.to_s.upcase)
      end
    end
  end
end
