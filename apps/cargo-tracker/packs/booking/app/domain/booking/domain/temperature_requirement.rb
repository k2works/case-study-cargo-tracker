# frozen_string_literal: true

module Booking
  module Domain
    # 温度管理条件。最低/最高温度・温度単位（REFRIGERATED 時必須）。
    TemperatureRequirement = Data.define(:min_temperature, :max_temperature, :unit) do
      CELSIUS = "CELSIUS"
      FAHRENHEIT = "FAHRENHEIT"
      UNITS = [ CELSIUS, FAHRENHEIT ].freeze

      def initialize(min_temperature:, max_temperature:, unit:)
        raise ArgumentError, "最低温度は必須です" if min_temperature.nil?
        raise ArgumentError, "最高温度は必須です" if max_temperature.nil?
        raise ArgumentError, "温度単位が不正です: #{unit}" unless UNITS.include?(unit)
        if min_temperature > max_temperature
          raise ArgumentError, "最低温度は最高温度以下である必要があります"
        end

        super
      end
    end
  end
end
