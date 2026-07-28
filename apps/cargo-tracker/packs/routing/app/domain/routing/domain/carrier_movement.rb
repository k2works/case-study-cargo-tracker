# frozen_string_literal: true

module Routing
  module Domain
    # 運送区間（エンティティ）。出発地・到着地（UN/LOCODE）・出発/到着時刻の区間単位。
    CarrierMovement = Data.define(:departure_unlocode, :arrival_unlocode,
                                  :departure_date, :arrival_date, :seq_number) do
      def initialize(departure_unlocode:, arrival_unlocode:, departure_date:, arrival_date:, seq_number:)
        raise ArgumentError, "出発地は必須です" if blank?(departure_unlocode)
        raise ArgumentError, "到着地は必須です" if blank?(arrival_unlocode)
        raise ArgumentError, "出発地と到着地は異なる必要があります" if departure_unlocode == arrival_unlocode
        raise ArgumentError, "出発日時・到着日時は必須です" if departure_date.nil? || arrival_date.nil?
        raise ArgumentError, "出発日は到着日より前である必要があります" if departure_date >= arrival_date

        super
      end

      private

      def blank?(value)
        value.nil? || value.to_s.strip.empty?
      end
    end
  end
end
